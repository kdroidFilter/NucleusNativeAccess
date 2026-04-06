// Rust benchmark — mirrors the Kotlin/Native benchmark example.
// Same algorithms, same parameters, for fair Rust vs KN vs JVM comparison.

// ── Fibonacci ───────────────────────────────────────────────────────────────

pub struct FibCalculator {
    _dummy: i32,
}

impl FibCalculator {
    pub fn new() -> Self {
        FibCalculator { _dummy: 0 }
    }

    pub fn fib_recursive(&self, n: i32) -> i64 {
        if n <= 1 { return n as i64; }
        self.fib_recursive(n - 1) + self.fib_recursive(n - 2)
    }

    pub fn fib_iterative(&self, n: i32) -> i64 {
        if n <= 1 { return n as i64; }
        let mut a: i64 = 0;
        let mut b: i64 = 1;
        for _ in 0..(n - 1) {
            let tmp = a + b;
            a = b;
            b = tmp;
        }
        b
    }
}

// ── Pi calculation (Leibniz series) ─────────────────────────────────────────

pub struct PiCalculator {
    _dummy: i32,
}

impl PiCalculator {
    pub fn new() -> Self {
        PiCalculator { _dummy: 0 }
    }

    pub fn leibniz(&self, iterations: i32) -> f64 {
        let mut sum: f64 = 0.0;
        for i in 0..iterations {
            sum += (if i % 2 == 0 { 1.0 } else { -1.0 }) / (2 * i + 1) as f64;
        }
        sum * 4.0
    }

    pub fn nilakantha(&self, iterations: i32) -> f64 {
        let mut pi: f64 = 3.0;
        let mut sign: f64 = 1.0;
        for i in 1..=iterations {
            let n = (2.0 * i as f64) * (2.0 * i as f64 + 1.0) * (2.0 * i as f64 + 2.0);
            pi += sign * 4.0 / n;
            sign = -sign;
        }
        pi
    }

    pub fn monte_carlo_pi(&self, samples: i32, seed: i64) -> f64 {
        let mut inside: i32 = 0;
        let mut state: i64 = seed;
        for _ in 0..samples {
            state = state.wrapping_mul(6364136223846793005i64).wrapping_add(1442695040888963407i64);
            let x = ((state as u64) >> 33) as f64 / i32::MAX as f64;
            state = state.wrapping_mul(6364136223846793005i64).wrapping_add(1442695040888963407i64);
            let y = ((state as u64) >> 33) as f64 / i32::MAX as f64;
            if x * x + y * y <= 1.0 { inside += 1; }
        }
        4.0 * inside as f64 / samples as f64
    }
}

// ── Array/collection processing ─────────────────────────────────────────────

pub struct ArrayProcessor {
    _dummy: i32,
}

impl ArrayProcessor {
    pub fn new() -> Self {
        ArrayProcessor { _dummy: 0 }
    }

    pub fn sum_array(&self, size: i32) -> i64 {
        let mut sum: i64 = 0;
        for i in 0..size {
            sum += i as i64;
        }
        sum
    }

    pub fn bubble_sort_size(&self, size: i32) -> i32 {
        let size = size as usize;
        let mut arr: Vec<i32> = (0..size).map(|i| (size - i) as i32).collect();
        for i in 0..size {
            for j in 0..(size - i - 1) {
                if arr[j] > arr[j + 1] {
                    arr.swap(j, j + 1);
                }
            }
        }
        arr[0]
    }
}

// ── String processing ───────────────────────────────────────────────────────

pub struct StringProcessor {
    _dummy: i32,
}

impl StringProcessor {
    pub fn new() -> Self {
        StringProcessor { _dummy: 0 }
    }

    pub fn concat_loop(&self, iterations: i32) -> i32 {
        let mut s = String::new();
        for i in 0..iterations {
            s.push_str(&i.to_string());
        }
        s.len() as i32
    }

    pub fn reverse_string(&self, s: String) -> String {
        s.chars().rev().collect()
    }

    pub fn count_chars(&self, s: String, c: String) -> i32 {
        let ch = c.chars().next().unwrap_or('\0');
        s.chars().filter(|&x| x == ch).count() as i32
    }
}

// ── Object allocation stress ────────────────────────────────────────────────

pub struct Vec2 {
    pub x: f64,
    pub y: f64,
}

pub struct AllocationBench {
    _dummy: i32,
}

impl AllocationBench {
    pub fn new() -> Self {
        AllocationBench { _dummy: 0 }
    }

    pub fn allocate_points(&self, count: i32) -> f64 {
        let mut sum_x: f64 = 0.0;
        for i in 0..count {
            let p = Vec2 { x: i as f64, y: i as f64 * 2.0 };
            sum_x += p.x;
        }
        sum_x
    }

    pub fn get_vec(&self) -> Vec2 {
        Vec2 { x: 1.0, y: 2.0 }
    }

    pub fn sum_vec(&self, v: &Vec2) -> f64 {
        v.x + v.y
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_fib() {
        let calc = FibCalculator::new();
        assert_eq!(calc.fib_recursive(10), 55);
        assert_eq!(calc.fib_iterative(10), 55);
    }

    #[test]
    fn test_pi() {
        let calc = PiCalculator::new();
        let pi = calc.leibniz(1_000_000);
        assert!((pi - std::f64::consts::PI).abs() < 0.001);
    }

    #[test]
    fn test_array() {
        let proc = ArrayProcessor::new();
        assert_eq!(proc.sum_array(100), 4950);
        assert_eq!(proc.bubble_sort_size(10), 1);
    }

    #[test]
    fn test_string() {
        let proc = StringProcessor::new();
        assert_eq!(proc.reverse_string("hello".to_string()), "olleh");
        assert_eq!(proc.count_chars("hello world".to_string(), "l".to_string()), 3);
    }
}

include!(concat!(env!("OUT_DIR"), "/kne_bridges.rs"));
