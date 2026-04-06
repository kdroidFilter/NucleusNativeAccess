# Symphonia Bridge — 4 Remaining Rust Compilation Errors

## Summary

The `rust-symphonia` example imports both `symphonia` (audio decoding) and `cpal` (audio output) as Rust crates via NNA's `crate()` DSL. The bridge generator successfully processes ~700 bridge functions with 0 warnings, but 4 methods on the `Fft` class fail to compile.

All 4 errors have the **same root cause**: methods that take `&[Complex]` or `&mut [Complex]` slice parameters, where `Complex` is a Rust struct (`Complex { re: f32, im: f32 }`).

## The 4 Failing Methods

| Method | Rust Signature | Error |
|--------|---------------|-------|
| `Fft::ifft` | `fn ifft(&self, x: &[Complex], y: &mut [Complex])` | `expected &[Complex], found &[i64]` |
| `Fft::ifft_inplace` | `fn ifft_inplace(&self, x: &mut [Complex])` | `expected &mut [Complex], found &[i64]` |
| `Fft::fft_inplace` | `fn fft_inplace(&self, x: &mut [Complex])` | `expected &mut [Complex], found &[i64]` |
| `Fft::fft` | `fn fft(&self, x: &[Complex], y: &mut [Complex])` | `expected &[Complex], found &[i64]` |

## Root Cause

The bridge generator supports `List<OBJECT>` as a **return type** (by writing i64 handles into an output buffer). But when `List<OBJECT>` appears as a **parameter**, the bridge passes the slice as `&[i64]` (array of opaque handles), while Rust expects `&[Complex]` (array of actual struct values).

The fundamental mismatch:
- **Return** `&[Complex]`: the bridge iterates the Rust slice, converts each `Complex` to a boxed handle (`Box::into_raw`), and writes the handle into an output i64 buffer. The JVM side reads handles and wraps them in proxy objects. This works.
- **Parameter** `&[Complex]`: the JVM sends an array of i64 handles. The bridge creates a `&[i64]` slice from the pointer. But Rust expects `&[Complex]` — an array of actual 8-byte structs (`re: f32, im: f32`), not an array of 8-byte pointers to heap-allocated boxes.

## Why This Can't Be Fixed With the Current Approach

For `&[Complex]` parameters, the bridge would need to:
1. Receive an array of i64 handles from the JVM
2. Dereference each handle to get the `Complex` struct (`*Box::from_raw(h as *mut Complex)`)
3. Collect them into a `Vec<Complex>`
4. Pass `&vec` as the slice

This is feasible but has ownership issues:
- Step 2 **consumes** the Box (takes ownership), so the JVM proxy object becomes invalid after the call
- For `&mut [Complex]` params, changes to the slice elements must be propagated back to the JVM side

A proper implementation would require:
- A new bridge pattern: "object slice parameter" that borrows from handles without consuming them (`&**Box::from_raw(...)` pattern or `ManuallyDrop`)
- For `&mut [Complex]` params, a writeback mechanism to update the handles after the call

## Affected Types

Only `symphonia::core::dsp::complex::Complex` triggers this in the symphonia example. The `Complex` struct is:
```rust
pub struct Complex {
    pub re: f32,
    pub im: f32,
}
```

Other `List<OBJECT>` parameters in symphonia (like `List<Tag>`, `List<CuePoint>`) are in **constructors** that have already been excluded by the `hasUnbridgeableParam` filter because their containing structs have other unbridgeable fields.

## Workaround

The 4 methods (`fft`, `ifft`, `fft_inplace`, `ifft_inplace`) are DSP-internal methods on the `Fft` struct. They are not part of the typical Symphonia audio decoding workflow (probe → format reader → decoder → sample buffer). The main Symphonia API for audio processing works correctly.

## Fix Path

To properly support `&[OBJECT]` parameters, `RustBridgeGenerator.kt` needs:

1. In `appendParamConversion` for `KneType.LIST` with `OBJECT` element type:
   ```rust
   // Current (broken): passes &[i64] directly
   let x_slice = unsafe { std::slice::from_raw_parts(x_ptr, x_len as usize) };

   // Fix: dereference handles into a temporary Vec
   let x_handles = unsafe { std::slice::from_raw_parts(x_ptr, x_len as usize) };
   let x_vec: Vec<Complex> = x_handles.iter()
       .map(|&h| unsafe { std::ptr::read(h as *const Complex) })
       .collect();
   let x_slice = &x_vec;
   ```

2. For `&mut [OBJECT]` params, after the method call, write back modified values to the handles.

3. The `FfmProxyGenerator.kt` already handles `List<OBJECT>` params correctly on the JVM side (passing i64 handles).
