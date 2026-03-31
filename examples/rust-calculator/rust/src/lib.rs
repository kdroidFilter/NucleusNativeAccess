// Rust Calculator — mirrors the Kotlin/Native Calculator example.
// This serves as the reference implementation to validate NNA's Rust support.

/// Arithmetic operations supported by the calculator.
pub enum Operation {
    Add,
    Subtract,
    Multiply,
}

/// Result of a calculator operation — demonstrates tagged enum (sealed class).
pub enum CalcResult {
    /// A successful integer result.
    Value(i32),
    /// An error with a message.
    Error(String),
    /// A partial result with value and confidence.
    Partial { value: i32, confidence: f64 },
    /// No result available.
    Nothing,
}

/// Simple 2D point (data class -- all public fields, no complex methods).
pub struct Point {
    pub x: i32,
    pub y: i32,
}

/// A named value (data class -- mirrors Kotlin NamedValue).
pub struct NamedValue {
    pub name: String,
    pub value: i32,
}

// ── Traits (→ Kotlin interfaces) ────────────────────────────────────────

/// Something that can describe itself.
pub trait Describable {
    fn describe_self(&self) -> String;
}

/// Something that can be reset to its initial state.
pub trait Resettable {
    fn reset_to_default(&mut self);
}

/// Something that can measure a numeric value.
pub trait Measurable {
    fn measure(&self) -> f64;
    fn unit(&self) -> String;
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

    // ── Nullable params (Option<T>) ────────────────────────────────────

    pub fn add_optional(&mut self, value: Option<i32>) -> i32 {
        if let Some(v) = value {
            self.accumulator += v;
        }
        self.accumulator
    }

    pub fn set_nickname(&mut self, name: Option<String>) {
        self.nickname = name;
    }

    pub fn get_nickname(&self) -> Option<String> {
        self.nickname.clone()
    }

    pub fn add_point_or_null(&mut self, p: Option<&Point>) -> i32 {
        if let Some(pt) = p {
            self.accumulator += pt.x + pt.y;
        }
        self.accumulator
    }

    // ── Sealed enum support ────────────────────────────────────────────

    pub fn try_divide(&self, divisor: i32) -> CalcResult {
        if divisor == 0 {
            CalcResult::Error("Division by zero".to_string())
        } else if self.accumulator == 0 {
            CalcResult::Nothing
        } else if self.accumulator % divisor != 0 {
            let value = self.accumulator / divisor;
            let confidence = 1.0 - ((self.accumulator % divisor) as f64 / self.accumulator as f64).abs();
            CalcResult::Partial { value, confidence }
        } else {
            CalcResult::Value(self.accumulator / divisor)
        }
    }

    pub fn last_result(&self) -> CalcResult {
        if self.accumulator > 0 {
            CalcResult::Value(self.accumulator)
        } else if self.accumulator == 0 {
            CalcResult::Nothing
        } else {
            CalcResult::Error(format!("Negative value: {}", self.accumulator))
        }
    }

    // ── Data class support ────────────────────────────────────────────

    pub fn get_point(&self) -> Point {
        Point { x: self.accumulator, y: self.accumulator * 2 }
    }

    pub fn add_point(&mut self, p: &Point) -> i32 {
        self.accumulator += p.x + p.y;
        self.accumulator
    }

    pub fn get_named_value(&self) -> NamedValue {
        let name = if self.label.is_empty() { "default".to_string() } else { self.label.clone() };
        NamedValue { name, value: self.accumulator }
    }

    pub fn set_from_named(&mut self, nv: &NamedValue) {
        self.accumulator = nv.value;
        self.label = nv.name.clone();
    }

    // ── ByteArray support ─────────────────────────────────────────────

    pub fn to_bytes(&self) -> Vec<u8> {
        self.accumulator.to_string().into_bytes()
    }

    pub fn sum_bytes(&mut self, data: &[u8]) -> i32 {
        self.accumulator = data.iter().map(|&b| b as i32).sum();
        self.accumulator
    }

    pub fn reverse_bytes(&self, data: &[u8]) -> Vec<u8> {
        data.iter().rev().copied().collect()
    }

    // ── Collection support ──────────────────────────────────────────────

    pub fn get_recent_scores(&self) -> Vec<i32> {
        vec![self.accumulator, self.accumulator * 2, self.accumulator * 3]
    }

    // ── Async/suspend-like methods ────────────────────────────────────
    // Functions annotated with `@kne:suspend` in doc comments are bridged
    // as Kotlin suspend functions. The bridge spawns a thread, calls the
    // function, then invokes the continuation callback.

    /// Adds value after a delay and returns the new accumulator.
    /// @kne:suspend
    pub fn delayed_add(&mut self, value: i32, delay_ms: i32) -> i32 {
        std::thread::sleep(std::time::Duration::from_millis(delay_ms as u64));
        self.accumulator += value;
        self.accumulator
    }

    /// Returns a description string after a delay.
    /// @kne:suspend
    pub fn delayed_describe(&self, delay_ms: i32) -> String {
        std::thread::sleep(std::time::Duration::from_millis(delay_ms as u64));
        format!("Calculator(current={})", self.accumulator)
    }

    /// Panics after a delay (tests suspend error propagation).
    /// @kne:suspend
    pub fn fail_after_delay(&self, delay_ms: i32) -> String {
        std::thread::sleep(std::time::Duration::from_millis(delay_ms as u64));
        panic!("Intentional delayed error");
    }

    /// Does nothing after a delay (suspend returning Unit).
    /// @kne:suspend
    pub fn delayed_noop(&self, delay_ms: i32) {
        std::thread::sleep(std::time::Duration::from_millis(delay_ms as u64));
    }

    /// Returns whether accumulator is positive, after a delay.
    /// @kne:suspend
    pub fn delayed_is_positive(&self, delay_ms: i32) -> bool {
        std::thread::sleep(std::time::Duration::from_millis(delay_ms as u64));
        self.accumulator > 0
    }
    // ── Flow-like methods ────────────────────────────────────────────
    // Functions annotated with `@kne:flow(ElementType)` are bridged as
    // Kotlin Flow<T>. The bridge iterates the returned Vec and calls
    // onNext for each element.

    /// Emits integers from 1 to max with interval_ms delay between each.
    /// @kne:flow(Int)
    pub fn count_up(&self, max: i32, interval_ms: i32) -> Vec<i32> {
        let mut result = Vec::new();
        for i in 1..=max {
            std::thread::sleep(std::time::Duration::from_millis(interval_ms as u64));
            result.push(self.accumulator + i);
        }
        result
    }

    /// Emits score labels as strings.
    /// @kne:flow(String)
    pub fn score_labels(&self, count: i32) -> Vec<String> {
        (1..=count).map(|i| format!("Score #{}: {}", i, self.accumulator * i)).collect()
    }
}

// ── Trait implementations ───────────────────────────────────────────────

impl Describable for Calculator {
    fn describe_self(&self) -> String {
        format!("Calculator(current={}, label={})", self.accumulator, self.label)
    }
}

impl Resettable for Calculator {
    fn reset_to_default(&mut self) {
        self.accumulator = 0;
        self.label = String::new();
        self.scale = 1.0;
        self.enabled = true;
    }
}

impl Measurable for Calculator {
    fn measure(&self) -> f64 {
        self.accumulator as f64 * self.scale
    }

    fn unit(&self) -> String {
        "units".to_string()
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

/// Adds all numbers in a slice.
pub fn sum_all(numbers: &[i32]) -> i32 {
    numbers.iter().sum()
}

/// Finds the maximum value in a slice, or None if empty.
pub fn find_max(numbers: &[i32]) -> Option<i32> {
    numbers.iter().copied().max()
}

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
        assert_eq!(calc.echo("hello".to_string()), "hello");
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
        assert_eq!(greet("World".to_string()), "Hello, World!");
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

include!(concat!(env!("OUT_DIR"), "/kne_bridges.rs"));
