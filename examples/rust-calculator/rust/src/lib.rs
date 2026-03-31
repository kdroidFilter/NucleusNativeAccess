// Rust Calculator — mirrors the Kotlin/Native Calculator example.
// This serves as the reference implementation to validate NNA's Rust support.

/// Arithmetic operations supported by the calculator.
pub enum Operation {
    Add,
    Subtract,
    Multiply,
}

/// Simple 2D point.
pub struct Point {
    pub x: i32,
    pub y: i32,
}

impl Point {
    pub fn new(x: i32, y: i32) -> Self {
        Point { x, y }
    }
}

/// A stateful calculator that accumulates a value.
///
/// Mirrors the Kotlin/Native Calculator class:
/// - Mutable accumulator with arithmetic operations
/// - All primitive type conversions
/// - String operations
/// - Enum support
/// - Nullable returns (via Option)
/// - Error propagation (via panic)
pub struct Calculator {
    accumulator: i32,
    label: String,
    scale: f64,
    enabled: bool,
    last_operation: Operation,
    nickname: Option<String>,
}

impl Calculator {
    // ── Constructor ─────────────────────────────────────────────────────

    pub fn new(initial: i32) -> Self {
        Calculator {
            accumulator: initial,
            label: String::new(),
            scale: 1.0,
            enabled: true,
            last_operation: Operation::Add,
            nickname: None,
        }
    }

    // ── Int methods ─────────────────────────────────────────────────────

    pub fn add(&mut self, value: i32) -> i32 {
        self.accumulator += value;
        self.accumulator
    }

    pub fn subtract(&mut self, value: i32) -> i32 {
        self.accumulator -= value;
        self.accumulator
    }

    pub fn multiply(&mut self, value: i32) -> i32 {
        self.accumulator *= value;
        self.accumulator
    }

    pub fn reset(&mut self) {
        self.accumulator = 0;
    }

    pub fn divide(&mut self, divisor: i32) -> i32 {
        if divisor == 0 {
            panic!("Division by zero");
        }
        self.accumulator /= divisor;
        self.accumulator
    }

    pub fn fail_always(&self) -> String {
        panic!("Intentional error for testing");
    }

    pub fn get_current(&self) -> i32 {
        self.accumulator
    }

    // ── All primitive types as params and returns ───────────────────────

    pub fn add_long(&self, value: i64) -> i64 {
        (self.accumulator as i64) + value
    }

    pub fn add_double(&self, value: f64) -> f64 {
        (self.accumulator as f64) + value
    }

    pub fn add_float(&self, value: f32) -> f32 {
        (self.accumulator as f32) + value
    }

    pub fn add_short(&self, value: i16) -> i16 {
        (self.accumulator as i16).wrapping_add(value)
    }

    pub fn add_byte(&self, value: i8) -> i8 {
        (self.accumulator as i8).wrapping_add(value)
    }

    pub fn is_positive(&self) -> bool {
        self.accumulator > 0
    }

    pub fn check_flag(&self, flag: bool) -> bool {
        flag && self.accumulator > 0
    }

    // ── String methods ──────────────────────────────────────────────────

    pub fn describe(&self) -> String {
        format!("Calculator(current={})", self.accumulator)
    }

    pub fn echo(&self, text: String) -> String {
        text
    }

    pub fn concat(&self, a: String, b: String) -> String {
        format!("{}{}", a, b)
    }

    // ── Property accessors ──────────────────────────────────────────────

    pub fn get_label(&self) -> String {
        self.label.clone()
    }

    pub fn set_label(&mut self, label: String) {
        self.label = label;
    }

    pub fn get_scale(&self) -> f64 {
        self.scale
    }

    pub fn set_scale(&mut self, scale: f64) {
        self.scale = scale;
    }

    pub fn get_enabled(&self) -> bool {
        self.enabled
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    // ── Enum support ────────────────────────────────────────────────────

    pub fn apply_op(&mut self, op: &Operation, value: i32) -> i32 {
        match op {
            Operation::Add => self.add(value),
            Operation::Subtract => self.subtract(value),
            Operation::Multiply => self.multiply(value),
        }
    }

    // ── Nullable returns (Option<T>) ────────────────────────────────────

    pub fn divide_or_null(&self, divisor: i32) -> Option<i32> {
        if divisor != 0 {
            Some(self.accumulator / divisor)
        } else {
            None
        }
    }

    pub fn describe_or_null(&self) -> Option<String> {
        if self.accumulator > 0 {
            Some(format!("Positive({})", self.accumulator))
        } else {
            None
        }
    }

    pub fn is_positive_or_null(&self) -> Option<bool> {
        if self.accumulator == 0 {
            None
        } else {
            Some(self.accumulator > 0)
        }
    }

    pub fn to_long_or_null(&self) -> Option<i64> {
        if self.accumulator != 0 {
            Some(self.accumulator as i64)
        } else {
            None
        }
    }

    pub fn to_double_or_null(&self) -> Option<f64> {
        if self.accumulator != 0 {
            Some(self.accumulator as f64)
        } else {
            None
        }
    }

    // ── Data class support (Point) ──────────────────────────────────────

    pub fn get_point(&self) -> Point {
        Point::new(self.accumulator, self.accumulator * 2)
    }

    pub fn add_point(&mut self, p: &Point) -> i32 {
        self.accumulator += p.x + p.y;
        self.accumulator
    }

    // ── ByteArray support ─────────────────────────────────────────────

    pub fn to_bytes(&self) -> Vec<u8> {
        self.accumulator.to_string().into_bytes()
    }

    // sum_bytes and reverse_bytes take &[u8] slices — not yet supported in NNA bridge v1

    // ── Collection support ──────────────────────────────────────────────

    pub fn get_recent_scores(&self) -> Vec<i32> {
        vec![self.accumulator, self.accumulator * 2, self.accumulator * 3]
    }
}

// ── Top-level functions ─────────────────────────────────────────────────

/// Computes a binary operation on two integers.
pub fn compute(a: i32, b: i32, op: &Operation) -> i32 {
    match op {
        Operation::Add => a + b,
        Operation::Subtract => a - b,
        Operation::Multiply => a * b,
    }
}

/// Returns a greeting message.
pub fn greet(name: String) -> String {
    format!("Hello, {}!", name)
}

// sum_all and find_max take &[i32] slices — not yet supported in NNA bridge v1

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_calculator_basic() {
        let mut calc = Calculator::new(0);
        assert_eq!(calc.add(5), 5);
        assert_eq!(calc.add(3), 8);
        assert_eq!(calc.get_current(), 8);
    }

    #[test]
    fn test_calculator_subtract() {
        let mut calc = Calculator::new(10);
        assert_eq!(calc.subtract(3), 7);
    }

    #[test]
    fn test_calculator_multiply() {
        let mut calc = Calculator::new(4);
        assert_eq!(calc.multiply(3), 12);
    }

    #[test]
    fn test_calculator_describe() {
        let calc = Calculator::new(42);
        assert_eq!(calc.describe(), "Calculator(current=42)");
    }

    #[test]
    fn test_calculator_echo() {
        let calc = Calculator::new(0);
        assert_eq!(calc.echo("hello"), "hello");
    }

    #[test]
    fn test_calculator_all_primitives() {
        let calc = Calculator::new(10);
        assert_eq!(calc.add_long(5), 15);
        assert!((calc.add_double(3.5) - 13.5).abs() < 0.001);
        assert!((calc.add_float(2.5) - 12.5).abs() < 0.01);
        assert_eq!(calc.add_short(5), 15);
        assert_eq!(calc.add_byte(3), 13);
        assert!(calc.is_positive());
        assert!(calc.check_flag(true));
    }

    #[test]
    fn test_operation_enum() {
        let mut calc = Calculator::new(10);
        assert_eq!(calc.apply_op(&Operation::Add, 5), 15);
        assert_eq!(calc.apply_op(&Operation::Subtract, 3), 12);
        assert_eq!(calc.apply_op(&Operation::Multiply, 2), 24);
    }

    #[test]
    fn test_nullable_returns() {
        let calc = Calculator::new(10);
        assert_eq!(calc.divide_or_null(2), Some(5));
        assert_eq!(calc.divide_or_null(0), None);
        assert_eq!(calc.describe_or_null(), Some("Positive(10)".to_string()));
    }

    #[test]
    fn test_greet() {
        assert_eq!(greet("World"), "Hello, World!");
    }

    #[test]
    fn test_find_max() {
        assert_eq!(find_max(&[1, 5, 3]), Some(5));
        assert_eq!(find_max(&[]), None);
    }

    #[test]
    fn test_compute() {
        assert_eq!(compute(3, 4, &Operation::Add), 7);
        assert_eq!(compute(10, 3, &Operation::Subtract), 7);
        assert_eq!(compute(3, 4, &Operation::Multiply), 12);
    }
}






















include!("kne_bridges.rs");
