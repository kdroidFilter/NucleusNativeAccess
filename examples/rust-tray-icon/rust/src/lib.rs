use tray_icon::menu::{Menu, MenuItem, PredefinedMenuItem, MenuEvent};
use tray_icon::TrayIconEvent;

thread_local! {
    static TRAY: std::cell::RefCell<Option<tray_icon::TrayIcon>> = const { std::cell::RefCell::new(None) };
    static MENU: std::cell::RefCell<Option<Menu>> = const { std::cell::RefCell::new(None) };
}

/// Creates an icon from RGBA color values with the given pixel size.
/// Returns an opaque `Icon` handle that can be passed to `create_tray_with_icon` or `update_icon`.
pub fn make_icon(r: i32, g: i32, b: i32, size: i32) -> tray_icon::Icon {
    let pixel = [r as u8, g as u8, b as u8, 255u8];
    let rgba: Vec<u8> = pixel.iter().copied().cycle().take((size as usize * size as usize * 4) as usize).collect();
    tray_icon::Icon::from_rgba(rgba, size as u32, size as u32).expect("valid icon")
}

fn make_icon_internal(r: u8, g: u8, b: u8, size: u32) -> tray_icon::Icon {
    make_icon(r as i32, g as i32, b as i32, size as i32)
}

/// Creates a tray icon with icon, tooltip, title, and a context menu.
/// menu_items is a |-separated list of labels (e.g. "Open|Settings|About").
pub fn create_tray(
    icon_r: i32,
    icon_g: i32,
    icon_b: i32,
    tooltip: Option<&str>,
    title: Option<&str>,
    menu_items: Option<&str>,
) -> Result<(), String> {
    let tooltip = tooltip.map(|s| s.to_string());
    let title = title.map(|s| s.to_string());
    let menu_items = menu_items.map(|s| s.to_string());

    on_main_sync(move || {
        let icon = make_icon_internal(icon_r as u8, icon_g as u8, icon_b as u8, 32);
        let mut builder = tray_icon::TrayIconBuilder::new().with_icon(icon);
        if let Some(ref t) = tooltip {
            builder = builder.with_tooltip(t);
        }
        if let Some(ref t) = title {
            builder = builder.with_title(t);
        }

        // Build context menu
        let menu = Menu::new();
        if let Some(ref items_str) = menu_items {
            for label in items_str.split('|') {
                let label = label.trim();
                if !label.is_empty() {
                    let _ = menu.append(&MenuItem::new(label, true, None));
                }
            }
            let _ = menu.append(&PredefinedMenuItem::separator());
        }
        let _ = menu.append(&MenuItem::new("Quit", true, None));
        builder = builder.with_menu(Box::new(menu.clone()));
        MENU.with(|cell| *cell.borrow_mut() = Some(menu));

        let tray = builder.build().map_err(|e| e.to_string())?;
        TRAY.with(|cell| *cell.borrow_mut() = Some(tray));
        Ok(())
    })
}

/// Creates a tray icon using an opaque `Icon` handle (from `make_icon`).
pub fn create_tray_with_icon(
    icon: &tray_icon::Icon,
    tooltip: Option<&str>,
    title: Option<&str>,
    menu_items: Option<&str>,
) -> Result<(), String> {
    let icon = icon.clone();
    let tooltip = tooltip.map(|s| s.to_string());
    let title = title.map(|s| s.to_string());
    let menu_items = menu_items.map(|s| s.to_string());

    on_main_sync(move || {
        let mut builder = tray_icon::TrayIconBuilder::new().with_icon(icon);
        if let Some(ref t) = tooltip {
            builder = builder.with_tooltip(t);
        }
        if let Some(ref t) = title {
            builder = builder.with_title(t);
        }

        let menu = Menu::new();
        if let Some(ref items_str) = menu_items {
            for label in items_str.split('|') {
                let label = label.trim();
                if !label.is_empty() {
                    let _ = menu.append(&MenuItem::new(label, true, None));
                }
            }
            let _ = menu.append(&PredefinedMenuItem::separator());
        }
        let _ = menu.append(&MenuItem::new("Quit", true, None));
        builder = builder.with_menu(Box::new(menu.clone()));
        MENU.with(|cell| *cell.borrow_mut() = Some(menu));

        let tray = builder.build().map_err(|e| e.to_string())?;
        TRAY.with(|cell| *cell.borrow_mut() = Some(tray));
        Ok(())
    })
}

/// Updates the tray icon using an opaque `Icon` handle (from `make_icon`).
pub fn update_icon(icon: &tray_icon::Icon) {
    let icon = icon.clone();
    on_main_sync(move || {
        TRAY.with(|cell| {
            if let Some(ref tray) = *cell.borrow() {
                let _ = tray.set_icon(Some(icon));
            }
        });
    });
}

/// Destroys the current tray icon.
pub fn destroy_tray() {
    on_main_sync(|| {
        TRAY.with(|cell| *cell.borrow_mut() = None);
        MENU.with(|cell| *cell.borrow_mut() = None);
    });
}

/// Returns true if a tray icon is currently active.
pub fn is_active() -> bool {
    on_main_sync(|| TRAY.with(|cell| cell.borrow().is_some()))
}

/// Registers a callback for tray icon events (click, double_click, enter, leave, move).
/// The callback receives a description string. Pass None to clear the handler.
pub fn on_tray_event(handler: Option<impl Fn(String) + Send + Sync + 'static>) {
    match handler {
        Some(h) => {
            TrayIconEvent::set_event_handler(Some(move |event: TrayIconEvent| {
                let desc = match event {
                    TrayIconEvent::Click { button, button_state, .. } =>
                        format!("click|{:?}|{:?}", button, button_state),
                    TrayIconEvent::DoubleClick { button, .. } =>
                        format!("double_click|{:?}", button),
                    TrayIconEvent::Enter { .. } => "enter".to_string(),
                    TrayIconEvent::Leave { .. } => "leave".to_string(),
                    TrayIconEvent::Move { .. } => "move".to_string(),
                    _ => format!("{:?}", event),
                };
                h(desc);
            }));
        }
        None => {
            TrayIconEvent::set_event_handler(None::<fn(TrayIconEvent)>);
        }
    }
}

/// Registers a callback for menu item clicks.
/// The callback receives the menu item label. Pass None to clear the handler.
pub fn on_menu_event(handler: Option<impl Fn(String) + Send + Sync + 'static>) {
    match handler {
        Some(h) => {
            MenuEvent::set_event_handler(Some(move |event: MenuEvent| {
                let id = event.id;
                let label = MENU.with(|cell| {
                    if let Some(ref menu) = *cell.borrow() {
                        for item in menu.items() {
                            if item.id() == &id {
                                return match &item {
                                    tray_icon::menu::MenuItemKind::MenuItem(m) => m.text(),
                                    tray_icon::menu::MenuItemKind::Check(m) => m.text(),
                                    tray_icon::menu::MenuItemKind::Submenu(m) => m.text(),
                                    _ => id.0.clone(),
                                };
                            }
                        }
                    }
                    id.0.clone()
                });
                h(label);
            }));
        }
        None => {
            MenuEvent::set_event_handler(None::<fn(MenuEvent)>);
        }
    }
}

/// Updates the icon color.
pub fn set_icon_color(r: i32, g: i32, b: i32) {
    on_main_sync(move || {
        TRAY.with(|cell| {
            if let Some(ref tray) = *cell.borrow() {
                let icon = make_icon_internal(r as u8, g as u8, b as u8, 32);
                let _ = tray.set_icon(Some(icon));
            }
        });
    });
}

/// Updates the tooltip.
pub fn set_tooltip(tooltip: &str) -> Result<(), String> {
    let s = tooltip.to_string();
    on_main_sync(move || {
        TRAY.with(|cell| {
            if let Some(ref tray) = *cell.borrow() {
                tray.set_tooltip(Some(s)).map_err(|e| e.to_string())
            } else {
                Ok(())
            }
        })
    })
}

/// Updates the title.
pub fn set_title(title: &str) {
    let s = if title.is_empty() { None } else { Some(title.to_string()) };
    on_main_sync(move || {
        TRAY.with(|cell| {
            if let Some(ref tray) = *cell.borrow() {
                tray.set_title(s.as_deref());
            }
        });
    });
}

/// Shows or hides the tray icon.
pub fn set_visible(visible: bool) {
    on_main_sync(move || {
        TRAY.with(|cell| {
            if let Some(ref tray) = *cell.borrow() {
                tray.set_visible(visible);
            }
        });
    });
}

/// Sets the icon as template (macOS).
pub fn set_icon_as_template(is_template: bool) {
    on_main_sync(move || {
        TRAY.with(|cell| {
            if let Some(ref tray) = *cell.borrow() {
                tray.set_icon_as_template(is_template);
            }
        });
    });
}

// -- macOS: dispatch_sync on the main queue --

#[cfg(target_os = "macos")]
fn on_main_sync<F, R>(f: F) -> R
where
    F: FnOnce() -> R + Send,
    R: Send,
{
    use std::ffi::c_void;
    extern "C" {
        #[link_name = "_dispatch_main_q"]
        static _dispatch_main_q: c_void;
        fn dispatch_sync_f(queue: *const c_void, context: *mut c_void, work: extern "C" fn(*mut c_void));
    }
    #[allow(unused_unsafe)]
    let main_queue = unsafe { &raw const _dispatch_main_q };
    struct Payload<F, R> { func: Option<F>, result: Option<R> }
    extern "C" fn trampoline<F, R>(ctx: *mut c_void) where F: FnOnce() -> R {
        let payload = unsafe { &mut *(ctx as *mut Payload<F, R>) };
        let func = payload.func.take().unwrap();
        payload.result = Some(func());
    }
    let mut payload = Payload::<F, R> { func: Some(f), result: None };
    unsafe {
        dispatch_sync_f(main_queue, &mut payload as *mut _ as *mut c_void, trampoline::<F, R>);
    }
    payload.result.unwrap()
}

#[cfg(not(target_os = "macos"))]
fn on_main_sync<F, R>(f: F) -> R
where
    F: FnOnce() -> R + Send,
    R: Send,
{
    f()
}

include!(concat!(env!("OUT_DIR"), "/kne_bridges.rs"));
