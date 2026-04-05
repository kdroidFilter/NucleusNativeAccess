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

/// A data container demonstrating sealed enum variants with collection fields.
pub enum DataPayload {
    /// A list of integer scores.
    Scores(Vec<i32>),
    /// A set of unique integers.
    UniqueIds(std::collections::HashSet<i32>),
    /// A key-value map (Int → Int).
    Mapping(std::collections::HashMap<i32, i32>),
    /// A list of string tags.
    Tags(Vec<String>),
    /// Empty payload.
    Empty,
}

/// Demonstrates sealed enum with multi-field TUPLE variants (N > 1).
pub enum ErrorInfo {
    /// Two-string tuple variant, like OpenDeviceError(String, String).
    DeviceError(String, String),
    /// Three-field mixed-type tuple variant.
    PropertyError(String, i32, String),
    /// Two-field tuple: int + string.
    CodedMessage(i32, String),
    /// Single-field tuple for reference.
    Simple(String),
    /// Unit variant.
    None,
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

    /// A function that never returns normally (diverges).
    /// Returns Rust's `!` (Never) type, bridged as `Nothing` in Kotlin.
    pub fn panic_always(&self) -> ! {
        panic!("This calculator has crashed");
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
            let confidence =
                1.0 - ((self.accumulator % divisor) as f64 / self.accumulator as f64).abs();
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

    // ── ErrorInfo sealed enum support ────────────────────────────────

    /// Returns an ErrorInfo based on the current state.
    pub fn get_error_info(&self) -> ErrorInfo {
        if self.accumulator == 0 {
            ErrorInfo::None
        } else if self.accumulator < 0 {
            ErrorInfo::DeviceError(
                "calculator".to_string(),
                format!("negative value: {}", self.accumulator),
            )
        } else if self.accumulator > 1000 {
            ErrorInfo::PropertyError(
                "accumulator".to_string(),
                self.accumulator,
                "value too large".to_string(),
            )
        } else if self.accumulator > 100 {
            ErrorInfo::CodedMessage(self.accumulator, format!("code_{}", self.accumulator))
        } else {
            ErrorInfo::Simple(format!("ok: {}", self.accumulator))
        }
    }

    // ── Callback support ────────────────────────────────────────────

    pub fn transform_and_sum(&self, values: &[i32], transform: fn(i32) -> i32) -> i32 {
        values.iter().map(|&v| transform(v)).sum()
    }

    pub fn for_each_score(&self, count: i32, callback: fn(i32)) {
        for i in 1..=count {
            callback(self.accumulator * i);
        }
    }

    /// Runs a tick loop, calling on_tick for each iteration.
    /// @kne:suspend
    pub fn run_tick_loop(&self, count: i32, interval_ms: i32, on_tick: fn(i32)) {
        for i in 1..=count {
            std::thread::sleep(std::time::Duration::from_millis(interval_ms as u64));
            on_tick(self.accumulator + i);
        }
    }

    // ── Data class support ────────────────────────────────────────────

    pub fn get_point(&self) -> Point {
        Point {
            x: self.accumulator,
            y: self.accumulator * 2,
        }
    }

    pub fn add_point(&mut self, p: &Point) -> i32 {
        self.accumulator += p.x + p.y;
        self.accumulator
    }

    pub fn get_named_value(&self) -> NamedValue {
        let name = if self.label.is_empty() {
            "default".to_string()
        } else {
            self.label.clone()
        };
        NamedValue {
            name,
            value: self.accumulator,
        }
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

    /// Returns score names as a list of strings.
    pub fn get_score_names(&self) -> Vec<String> {
        (1..=3)
            .map(|i| format!("score_{}", self.accumulator * i))
            .collect()
    }

    /// Returns ratios as a list of doubles.
    pub fn get_ratios(&self) -> Vec<f64> {
        vec![
            self.accumulator as f64,
            self.accumulator as f64 / 2.0,
            self.accumulator as f64 / 3.0,
        ]
    }

    /// Returns flags as a list of booleans.
    pub fn get_flags(&self) -> Vec<bool> {
        vec![
            self.accumulator > 0,
            self.accumulator > 10,
            self.accumulator > 100,
        ]
    }

    // ── Optional collection support ────────────────────────────────────────

    /// Returns optional scores — Some if accumulator > 0, None otherwise.
    pub fn get_optional_scores(&self) -> Option<Vec<i32>> {
        if self.accumulator > 0 {
            Some(vec![
                self.accumulator,
                self.accumulator * 2,
                self.accumulator * 3,
            ])
        } else {
            None
        }
    }

    /// Returns optional tags — Some if label is set, None otherwise.
    pub fn get_optional_tags(&self) -> Option<Vec<String>> {
        if self.label.is_empty() {
            None
        } else {
            Some(vec![self.label.clone(), format!("scale:{}", self.scale)])
        }
    }

    /// Returns optional metadata map.
    pub fn get_optional_metadata(&self) -> Option<std::collections::HashMap<String, i32>> {
        if self.accumulator == 0 {
            None
        } else {
            let mut map = std::collections::HashMap::new();
            map.insert("current".to_string(), self.accumulator);
            map.insert("scale".to_string(), self.scale as i32);
            Some(map)
        }
    }

    // ── Tuple support ─────────────────────────────────────────────────

    /// Returns coordinates as a tuple (x, y).
    pub fn get_coordinates(&self) -> (i32, i32) {
        (self.accumulator, self.accumulator * 2)
    }

    /// Returns a triple: (count, label, enabled).
    pub fn get_triple(&self) -> (i32, String, bool) {
        (self.accumulator, self.label.clone(), self.enabled)
    }

    /// Takes a tuple parameter and returns the sum.
    pub fn sum_tuple(&self, coords: (i32, i32)) -> i32 {
        self.accumulator + coords.0 + coords.1
    }

    /// Returns nested tuple.
    pub fn get_nested_tuple(&self) -> (i32, (String, bool)) {
        (self.accumulator, (self.label.clone(), self.enabled))
    }

    /// Returns deeply nested tuple (3 levels).
    pub fn get_deep_tuple(&self) -> (i32, (String, (bool, i32))) {
        (
            self.accumulator,
            (self.label.clone(), (self.enabled, self.accumulator * 3)),
        )
    }

    /// Returns tuple with two nested tuples.
    pub fn get_double_nested(&self) -> ((i32, i32), (String, bool)) {
        (
            (self.accumulator, self.accumulator * 2),
            (self.label.clone(), self.enabled),
        )
    }

    /// Returns nested tuple with all primitive types.
    pub fn get_typed_nested(&self) -> (i64, (f64, i32)) {
        (
            self.accumulator as i64 * 1000,
            (self.scale, self.accumulator),
        )
    }

    /// Returns tuple with a vector: (scores, label).
    pub fn get_with_scores(&self) -> (Vec<i32>, String) {
        (
            vec![self.accumulator, self.accumulator * 2, self.accumulator * 3],
            self.label.clone(),
        )
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
        (1..=count)
            .map(|i| format!("Score #{}: {}", i, self.accumulator * i))
            .collect()
    }

    // ── impl Trait return types ─────────────────────────────────────────

    /// Returns an iterator over the recent scores (impl Iterator<Item = i32>).
    pub fn iter_scores(&self) -> impl Iterator<Item = i32> {
        let acc = self.accumulator;
        (1..=3).map(move |i| acc * i)
    }

    /// Returns an iterator over score labels (impl Iterator<Item = String>).
    pub fn iter_labels(&self) -> impl Iterator<Item = String> {
        let acc = self.accumulator;
        (1..=3).map(move |i| format!("score_{}", acc * i))
    }

    /// Returns an empty iterator (edge case: zero elements).
    pub fn iter_empty(&self) -> impl Iterator<Item = i32> {
        std::iter::empty()
    }

    /// Returns a Display-able description (impl Display).
    pub fn display_value(&self) -> impl std::fmt::Display {
        format!("Calc({})", self.accumulator)
    }

    /// Returns impl ToString (mapped via .to_string()).
    pub fn as_string_repr(&self) -> impl ToString {
        self.accumulator
    }

    /// Returns an ExactSizeIterator (also recognized as Iterator).
    pub fn exact_scores(&self) -> impl ExactSizeIterator<Item = i32> {
        let acc = self.accumulator;
        vec![acc, acc * 2, acc * 3].into_iter()
    }

    /// Returns impl Iterator with a Result-wrapped return (canFail + implTrait).
    pub fn try_iter_scores(&self) -> Result<impl Iterator<Item = i32>, String> {
        if self.accumulator < 0 {
            Err("negative accumulator".to_string())
        } else {
            let acc = self.accumulator;
            Ok((1..=3).map(move |i| acc * i))
        }
    }

    /// impl Iterator<Item = bool> — tests boolean element type.
    pub fn iter_flags(&self) -> impl Iterator<Item = bool> {
        let acc = self.accumulator;
        vec![acc > 0, acc > 10, acc > 100].into_iter()
    }

    /// impl Iterator<Item = f64> — tests float element type.
    pub fn iter_ratios(&self) -> impl Iterator<Item = f64> {
        let acc = self.accumulator as f64;
        vec![acc / 2.0, acc / 3.0, acc / 4.0].into_iter()
    }

    /// impl Iterator<Item = i64> — tests Long element type.
    pub fn iter_big_values(&self) -> impl Iterator<Item = i64> {
        let acc = self.accumulator as i64;
        vec![acc * 1_000_000, acc * 2_000_000].into_iter()
    }

    /// Large iterator — triggers buffer overflow/retry logic (>4096 elements).
    pub fn iter_large(&self, count: i32) -> impl Iterator<Item = i32> {
        let acc = self.accumulator;
        (0..count).map(move |i| acc + i)
    }

    /// &mut self + impl Iterator — mutable receiver with impl Trait return.
    pub fn drain_and_iter(&mut self, n: i32) -> impl Iterator<Item = i32> {
        self.accumulator += n;
        let acc = self.accumulator;
        (1..=3).map(move |i| acc * i)
    }

    /// impl Display with unicode content.
    pub fn display_unicode(&self) -> impl std::fmt::Display {
        format!("計算機({})", self.accumulator)
    }

    /// impl Display with very long string (>8192 bytes, triggers buffer retry).
    pub fn display_long(&self) -> impl std::fmt::Display {
        format!("x{}", "A".repeat(10_000))
    }

    /// impl DoubleEndedIterator — tested to ensure trait recognition.
    pub fn iter_reversed(&self) -> impl DoubleEndedIterator<Item = i32> {
        let acc = self.accumulator;
        vec![acc, acc * 2, acc * 3].into_iter()
    }

    /// impl IntoIterator — tested to ensure trait recognition.
    pub fn iter_into(&self) -> impl IntoIterator<Item = i32> {
        let acc = self.accumulator;
        vec![acc, acc + 1, acc + 2]
    }

    /// impl Iterator + Send — multiple bounds, should still work.
    pub fn iter_sendable(&self) -> impl Iterator<Item = i32> + Send {
        let acc = self.accumulator;
        vec![acc, acc * 10].into_iter()
    }

    /// Result<impl Display, String> — fallible + Display.
    pub fn try_display(&self) -> Result<impl std::fmt::Display, String> {
        if self.accumulator < 0 {
            Err("negative".to_string())
        } else {
            Ok(format!("OK({})", self.accumulator))
        }
    }

    /// Panic during .collect() — tests error propagation from inside iterator.
    pub fn iter_panicking(&self) -> impl Iterator<Item = i32> {
        let acc = self.accumulator;
        (0..3).map(move |i| {
            if i == 2 && acc < 0 {
                panic!("iterator panic at index 2");
            }
            acc + i
        })
    }

    /// Companion/static method returning impl Iterator (no &self).
    pub fn fibonacci_iter(n: i32) -> impl Iterator<Item = i32> {
        let mut a = 0i32;
        let mut b = 1i32;
        (0..n).map(move |_| {
            let val = a;
            let next = a.wrapping_add(b);
            a = b;
            b = next;
            val
        })
    }

    /// Companion/static method returning impl Display (no &self).
    pub fn static_label(prefix: String, value: i32) -> impl std::fmt::Display {
        format!("{}={}", prefix, value)
    }
}

// ── Trait implementations ───────────────────────────────────────────────

impl Describable for Calculator {
    fn describe_self(&self) -> String {
        format!(
            "Calculator(current={}, label={})",
            self.accumulator, self.label
        )
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

/// Top-level function returning impl Iterator (generates a range).
pub fn generate_range(start: i32, end: i32) -> impl Iterator<Item = i32> {
    start..end
}

/// Top-level function returning impl Display.
pub fn format_pair(a: i32, b: i32) -> impl std::fmt::Display {
    format!("{} + {} = {}", a, b, a + b)
}

/// Top-level impl Into<String>.
pub fn into_greeting(name: String) -> impl Into<String> {
    format!("Hi, {}!", name)
}

/// Top-level impl AsRef<str>.
pub fn as_ref_label(value: i32) -> impl AsRef<str> {
    format!("label_{}", value)
}

/// Top-level impl Iterator<Item = String> with String param.
pub fn repeat_str(text: String, count: i32) -> impl Iterator<Item = String> {
    let t = text;
    (0..count).map(move |_| t.clone())
}

/// Top-level Result<impl Display, String>.
pub fn try_format(a: i32, b: i32) -> Result<impl std::fmt::Display, String> {
    if b == 0 {
        Err("division by zero".to_string())
    } else {
        Ok(format!("{} / {} = {}", a, b, a / b))
    }
}

// ── dyn Trait functions (trait objects) ──────────────────────────────────

/// Creates a Describable trait object from a Calculator.
pub fn create_describable(initial: i32) -> Box<dyn Describable> {
    Box::new(Calculator::new(initial))
}

/// Creates a Measurable trait object from a Calculator.
pub fn create_measurable(initial: i32) -> Box<dyn Measurable> {
    Box::new(Calculator::new(initial))
}

/// Creates a Resettable trait object from a Calculator with mutations.
pub fn create_resettable(initial: i32) -> Box<dyn Resettable> {
    Box::new(Calculator::new(initial))
}

/// Takes a reference to a dyn Describable and returns its description.
pub fn describe_trait_object(obj: &dyn Describable) -> String {
    obj.describe_self()
}

/// Takes a Box<dyn Measurable> and returns the measurement + unit.
pub fn measure_trait_object(obj: &dyn Measurable) -> String {
    format!("{} {}", obj.measure(), obj.unit())
}

/// Factory: returns Option<Box<dyn Describable>>.
pub fn maybe_create_describable(initial: i32) -> Option<Box<dyn Describable>> {
    if initial >= 0 {
        Some(Box::new(Calculator::new(initial)))
    } else {
        None
    }
}

/// Returns a Vec of dyn Describable trait objects.
pub fn create_describable_list(values: &[i32]) -> Vec<Box<dyn Describable>> {
    values
        .iter()
        .map(|&v| Box::new(Calculator::new(v)) as Box<dyn Describable>)
        .collect()
}

/// Mutates a dyn Resettable trait object.
pub fn reset_trait_object(obj: &mut dyn Resettable) {
    obj.reset_to_default();
}

/// Returns Result<Box<dyn Describable>, String>.
pub fn try_create_describable(initial: i32) -> Result<Box<dyn Describable>, String> {
    if initial == i32::MIN {
        Err("invalid initial value".to_string())
    } else {
        Ok(Box::new(Calculator::new(initial)))
    }
}

// ── DataPayload factory functions ──────────────────────────────────────────

/// Creates a Scores payload from a slice of ints.
pub fn create_scores_payload(values: &[i32]) -> DataPayload {
    DataPayload::Scores(values.to_vec())
}

/// Creates a UniqueIds payload from a slice of ints.
pub fn create_unique_ids_payload(ids: &[i32]) -> DataPayload {
    DataPayload::UniqueIds(ids.iter().cloned().collect())
}

/// Creates a Mapping payload from parallel key/value slices.
pub fn create_mapping_payload(keys: &[i32], values: &[i32]) -> DataPayload {
    let map: std::collections::HashMap<i32, i32> = keys
        .iter()
        .zip(values.iter())
        .map(|(k, v)| (*k, *v))
        .collect();
    DataPayload::Mapping(map)
}

/// Creates a Tags payload from an array of C string pointers.
pub fn create_tags_payload(tags_ptr: *const *const std::ffi::c_char, tags_len: i32) -> DataPayload {
    let tags_slice = unsafe { std::slice::from_raw_parts(tags_ptr, tags_len as usize) };
    let tags: Vec<String> = tags_slice
        .iter()
        .map(|&p| {
            if p.is_null() {
                String::new()
            } else {
                unsafe { std::ffi::CStr::from_ptr(p) }
                    .to_string_lossy()
                    .into_owned()
            }
        })
        .collect();
    DataPayload::Tags(tags)
}

/// Creates an Empty payload.
pub fn create_empty_payload() -> DataPayload {
    DataPayload::Empty
}

// ── Generic monomorphisation support ────────────────────────────────────

/// Trait for value transformation — used to test auto-monomorphisation.
pub trait ValueTransformer {
    fn transform(&self, input: i32) -> i32;
    fn transformer_name(&self) -> String;
}

/// Doubles the input value.
pub struct Doubler;

impl Doubler {
    pub fn new() -> Self {
        Doubler
    }
}

impl ValueTransformer for Doubler {
    fn transform(&self, input: i32) -> i32 {
        input * 2
    }
    fn transformer_name(&self) -> String {
        "Doubler".to_string()
    }
}

/// Triples the input value.
pub struct Tripler;

impl Tripler {
    pub fn new() -> Self {
        Tripler
    }
}

impl ValueTransformer for Tripler {
    fn transform(&self, input: i32) -> i32 {
        input * 3
    }
    fn transformer_name(&self) -> String {
        "Tripler".to_string()
    }
}

impl Calculator {
    /// Applies a generic transformer to the current accumulator.
    /// NNA should monomorphise this into apply_transformer_doubler and apply_transformer_tripler.
    pub fn apply_transformer<T: ValueTransformer>(&self, transformer: &T) -> i32 {
        transformer.transform(self.accumulator)
    }

    /// Returns the name of a generic transformer.
    pub fn get_transformer_name<T: ValueTransformer>(&self, transformer: &T) -> String {
        transformer.transformer_name()
    }
}

/// Top-level generic function — transforms a value using any ValueTransformer.
pub fn transform_value<T: ValueTransformer>(value: i32, transformer: &T) -> i32 {
    transformer.transform(value)
}

// ── Generic struct monomorphisation support ─────────────────────────────

/// A generic processor that wraps a ValueTransformer and adds an offset.
/// NNA should monomorphise this into `Processor_Doubler` and `Processor_Tripler`.
pub struct Processor<T: ValueTransformer> {
    transformer: T,
    offset: i32,
}

impl<T: ValueTransformer> Processor<T> {
    pub fn new(transformer: T, offset: i32) -> Self {
        Processor { transformer, offset }
    }

    pub fn process(&self, value: i32) -> i32 {
        self.transformer.transform(value) + self.offset
    }

    pub fn get_offset(&self) -> i32 {
        self.offset
    }

    pub fn set_offset(&mut self, offset: i32) {
        self.offset = offset;
    }

    pub fn name(&self) -> String {
        format!("Processor({}+{})", self.transformer.transformer_name(), self.offset)
    }
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

    #[test]
    fn test_impl_trait_iter_scores() {
        let calc = Calculator::new(5);
        let scores: Vec<i32> = calc.iter_scores().collect();
        assert_eq!(scores, vec![5, 10, 15]);
    }

    #[test]
    fn test_impl_trait_iter_labels() {
        let calc = Calculator::new(2);
        let labels: Vec<String> = calc.iter_labels().collect();
        assert_eq!(labels, vec!["score_2", "score_4", "score_6"]);
    }

    #[test]
    fn test_impl_trait_iter_empty() {
        let calc = Calculator::new(1);
        let empty: Vec<i32> = calc.iter_empty().collect();
        assert!(empty.is_empty());
    }

    #[test]
    fn test_impl_trait_display_value() {
        let calc = Calculator::new(42);
        assert_eq!(calc.display_value().to_string(), "Calc(42)");
    }

    #[test]
    fn test_impl_trait_generate_range() {
        let range: Vec<i32> = generate_range(1, 4).collect();
        assert_eq!(range, vec![1, 2, 3]);
    }

    #[test]
    fn test_impl_trait_format_pair() {
        assert_eq!(format_pair(3, 4).to_string(), "3 + 4 = 7");
    }

    #[test]
    fn test_impl_trait_try_iter_scores() {
        let calc = Calculator::new(5);
        let scores: Vec<i32> = calc.try_iter_scores().unwrap().collect();
        assert_eq!(scores, vec![5, 10, 15]);

        let neg = Calculator::new(-1);
        assert!(neg.try_iter_scores().is_err());
    }
}

include!(concat!(env!("OUT_DIR"), "/kne_bridges.rs"));
