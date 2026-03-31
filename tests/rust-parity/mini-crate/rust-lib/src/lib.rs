/// A simple calculator that accumulates a value.
pub struct Calculator {
    pub value: i32,
    pub name: String,
}

impl Calculator {
    /// Creates a new Calculator with an initial value.
    pub fn new(initial_value: i32, name: String) -> Self {
        Calculator {
            value: initial_value,
            name,
        }
    }

    /// Adds n to the current value and returns the result.
    pub fn add(&self, n: i32) -> i32 {
        self.value + n
    }

    /// Subtracts n from the current value and returns the result.
    pub fn subtract(&self, n: i32) -> i32 {
        self.value - n
    }

    /// Multiplies the current value by n and returns the result.
    pub fn multiply(&self, n: i32) -> i32 {
        self.value * n
    }

    /// Returns the current value.
    pub fn get_value(&self) -> i32 {
        self.value
    }

    /// Returns the name of this calculator.
    pub fn get_name(&self) -> String {
        self.name.clone()
    }

    /// Resets the calculator to a new value and returns the old value.
    pub fn reset(&mut self, new_value: i32) -> i32 {
        let old = self.value;
        self.value = new_value;
        old
    }
}

/// Supported arithmetic operations.
pub enum Operation {
    Add,
    Subtract,
    Multiply,
    Divide,
}

/// A simple point in 2D space.
pub struct Point {
    pub x: f64,
    pub y: f64,
}

impl Point {
    /// Creates a new Point.
    pub fn new(x: f64, y: f64) -> Self {
        Point { x, y }
    }

    /// Computes the distance to another point.
    pub fn distance_to(&self, other: &Point) -> f64 {
        ((self.x - other.x).powi(2) + (self.y - other.y).powi(2)).sqrt()
    }

    /// Returns a string representation.
    pub fn to_string_repr(&self) -> String {
        format!("({}, {})", self.x, self.y)
    }
}

/// Computes a binary operation on two integers.
pub fn compute(a: i32, b: i32, op: &Operation) -> i32 {
    match op {
        Operation::Add => a + b,
        Operation::Subtract => a - b,
        Operation::Multiply => a * b,
        Operation::Divide => {
            if b == 0 {
                0
            } else {
                a / b
            }
        }
    }
}

/// Adds all numbers in a list.
pub fn sum_all(numbers: Vec<i32>) -> i32 {
    numbers.iter().sum()
}

/// Returns a greeting message.
pub fn greet(name: &str) -> String {
    format!("Hello, {}!", name)
}

/// Finds the maximum value in a list, or None if empty.
pub fn find_max(numbers: Vec<i32>) -> Option<i32> {
    numbers.into_iter().max()
}
