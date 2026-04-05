use nokhwa::pixel_format::RgbFormat;
use nokhwa::utils::{
    CameraIndex, RequestedFormat, RequestedFormatType,
};
use std::sync::Mutex;

/// A camera device wrapping nokhwa's Camera.
pub struct Camera {
    inner: Mutex<nokhwa::Camera>,
    width: u32,
    height: u32,
}

impl Camera {
    /// Opens the default camera (index 0) with the highest available frame rate.
    pub fn new(width: i32, height: i32) -> Result<Self, String> {
        let requested = RequestedFormat::new::<RgbFormat>(
            RequestedFormatType::AbsoluteHighestFrameRate,
        );
        let mut cam = nokhwa::Camera::new(CameraIndex::Index(0), requested)
            .map_err(|e| e.to_string())?;
        cam.open_stream().map_err(|e| e.to_string())?;
        let resolution = cam.resolution();
        Ok(Camera {
            inner: Mutex::new(cam),
            width: if width > 0 { width as u32 } else { resolution.width_x },
            height: if height > 0 { height as u32 } else { resolution.height_y },
        })
    }

    /// Returns the camera resolution width.
    pub fn get_width(&self) -> i32 {
        self.width as i32
    }

    /// Returns the camera resolution height.
    pub fn get_height(&self) -> i32 {
        self.height as i32
    }

    /// Captures a single frame as raw RGB bytes.
    /// Returns None if the camera is not streaming.
    pub fn frame(&self) -> Option<Vec<u8>> {
        let mut cam = self.inner.lock().ok()?;
        let buffer = cam.frame().ok()?;
        let decoded = buffer.decode_image::<RgbFormat>().ok()?;
        Some(decoded.into_raw())
    }

    /// Stops the camera stream.
    pub fn stop(&self) {
        if let Ok(mut cam) = self.inner.lock() {
            let _ = cam.stop_stream();
        }
    }

    /// Returns whether the camera stream is active.
    pub fn get_is_streaming(&self) -> bool {
        self.inner
            .lock()
            .map(|cam| cam.is_stream_open())
            .unwrap_or(false)
    }
}

/// Lists available camera device names. Returns an empty list on error.
pub fn list_cameras() -> Vec<String> {
    nokhwa::query(nokhwa::utils::ApiBackend::Auto)
        .unwrap_or_default()
        .into_iter()
        .map(|info| info.human_name().to_string())
        .collect()
}

include!(concat!(env!("OUT_DIR"), "/kne_bridges.rs"));
