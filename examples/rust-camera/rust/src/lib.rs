use std::cell::UnsafeCell;

/// A synthetic camera that generates colored frames for demo purposes.
pub struct Camera {
    width: u32,
    height: u32,
    frame_count: UnsafeCell<u32>,
}

// SAFETY: FFI access through handles is single-threaded
unsafe impl Send for Camera {}
unsafe impl Sync for Camera {}

pub fn create_camera(width: u32, height: u32) -> Camera {
    Camera {
        width,
        height,
        frame_count: UnsafeCell::new(0),
    }
}

/// Returns an RGB frame as raw bytes, or None if dimensions are invalid.
pub fn camera_frame(camera: &Camera) -> Option<Vec<u8>> {
    if camera.width == 0 || camera.height == 0 {
        return None;
    }
    let count = unsafe { &mut *camera.frame_count.get() };
    *count = count.wrapping_add(1);
    let t = *count;

    let len = (camera.width * camera.height * 3) as usize;
    let mut buf = vec![0u8; len];
    for y in 0..camera.height {
        for x in 0..camera.width {
            let i = ((y * camera.width + x) * 3) as usize;
            // Animated gradient: shifts with each frame
            buf[i] = ((x.wrapping_add(t.wrapping_mul(2))) % 256) as u8;     // R
            buf[i + 1] = ((y.wrapping_add(t.wrapping_mul(3))) % 256) as u8; // G
            buf[i + 2] = ((x.wrapping_add(y).wrapping_add(t)) % 256) as u8; // B
        }
    }
    Some(buf)
}

pub fn camera_width(camera: &Camera) -> u32 {
    camera.width
}

pub fn camera_height(camera: &Camera) -> u32 {
    camera.height
}

include!(concat!(env!("OUT_DIR"), "/kne_bridges.rs"));
