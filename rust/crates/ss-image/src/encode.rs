use std::fs;
use std::io::Cursor;
use std::path::{Path, PathBuf};

use image::codecs::jpeg::JpegEncoder;
use image::codecs::png::PngEncoder;
use image::codecs::webp::WebPEncoder;
use image::imageops::FilterType;
use image::{ColorType, DynamicImage, ImageEncoder, ImageReader};
use ss_domain::Recipe;

use crate::error::ImageError;
use crate::exif::apply_exif_orientation;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ImageEncodeOptions {
    pub out_format: String,
    pub quality: u8,
    pub max_side: Option<u32>,
    pub keep_gps: bool,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ImageEncodeResult {
    pub output_bytes: u64,
    pub out_width: u32,
    pub out_height: u32,
    pub output_path: PathBuf,
}

pub fn options_from_recipe(recipe: &Recipe) -> Result<ImageEncodeOptions, ImageError> {
    let out_format = recipe
        .image_out_format
        .clone()
        .ok_or(ImageError::InvalidRecipe)?;
    let quality = recipe.image_quality.ok_or(ImageError::InvalidRecipe)?;
    Ok(ImageEncodeOptions {
        out_format,
        quality,
        max_side: recipe.max_side,
        keep_gps: false,
    })
}

pub fn compress_image(
    input: &Path,
    output: &Path,
    options: &ImageEncodeOptions,
    should_cancel: Option<&dyn Fn() -> bool>,
) -> Result<ImageEncodeResult, ImageError> {
    if cancelled(should_cancel) {
        return Err(ImageError::Cancelled);
    }
    if !input.is_file() {
        return Err(ImageError::Io(format!("input not found: {}", input.display())));
    }

    let bytes = fs::read(input).map_err(|e| ImageError::Io(e.to_string()))?;
    if cancelled(should_cancel) {
        return Err(ImageError::Cancelled);
    }
    let img = decode_with_max_side(&bytes, options.max_side)?;
    let img = apply_exif_orientation(img, input);
    if cancelled(should_cancel) {
        return Err(ImageError::Cancelled);
    }
    let (target_w, target_h) = fit_max_side(img.width(), img.height(), options.max_side);
    let img = if target_w != img.width() || target_h != img.height() {
        img.resize_exact(target_w, target_h, FilterType::Triangle)
    } else {
        img
    };
    if cancelled(should_cancel) {
        return Err(ImageError::Cancelled);
    }

    if let Some(parent) = output.parent() {
        fs::create_dir_all(parent).map_err(|e| ImageError::Io(e.to_string()))?;
    }

    let format = normalize_format(&options.out_format);
    match format.as_str() {
        "jpeg" | "jpg" => {
            encode_jpeg(&img, output, options.quality)?;
            crate::exif::copy_exif_metadata(input, output, options.keep_gps)?;
        }
        "webp" => {
            encode_webp(&img, output, options.quality)?;
            let has_alpha = img.color().has_alpha();
            let _ = crate::exif::copy_exif_to_webp(
                input,
                output,
                img.width(),
                img.height(),
                has_alpha,
                options.keep_gps,
            );
        }
        "png" => encode_png(&img, output)?,
        other => return Err(ImageError::UnsupportedFormat(other.to_string())),
    }

    let meta = fs::metadata(output).map_err(|e| ImageError::Io(e.to_string()))?;
    Ok(ImageEncodeResult {
        output_bytes: meta.len(),
        out_width: img.width(),
        out_height: img.height(),
        output_path: output.to_path_buf(),
    })
}

fn decode_with_max_side(bytes: &[u8], _max_side: Option<u32>) -> Result<DynamicImage, ImageError> {
    let mut reader = ImageReader::new(Cursor::new(bytes))
        .with_guessed_format()
        .map_err(|e| ImageError::Decode(e.to_string()))?;
    let mut limits = image::Limits::default();
    limits.max_alloc = Some(256 * 1024 * 1024); // 256 MB max buffer limit to prevent OS OOM kill
    limits.max_image_width = Some(16384);
    limits.max_image_height = Some(16384);
    reader.limits(limits);
    reader.decode().map_err(|e| ImageError::Decode(e.to_string()))
}

fn fit_max_side(width: u32, height: u32, max_side: Option<u32>) -> (u32, u32) {
    let max_side = max_side.unwrap_or(width.max(height));
    let long = width.max(height);
    if long <= max_side {
        return (width, height);
    }
    let scale = max_side as f32 / long as f32;
    (
        (width as f32 * scale).round().max(1.0) as u32,
        (height as f32 * scale).round().max(1.0) as u32,
    )
}

fn encode_jpeg(img: &DynamicImage, output: &Path, quality: u8) -> Result<(), ImageError> {
    let rgb = img.to_rgb8();
    let file = fs::File::create(output).map_err(|e| ImageError::Io(e.to_string()))?;
    let encoder = JpegEncoder::new_with_quality(file, quality);
    encoder
        .write_image(rgb.as_raw(), rgb.width(), rgb.height(), ColorType::Rgb8.into())
        .map_err(|e| ImageError::Encode(e.to_string()))?;
    Ok(())
}

fn encode_webp(img: &DynamicImage, output: &Path, quality: u8) -> Result<(), ImageError> {
    let rgba = img.to_rgba8();
    if quality >= 95 {
        let file = fs::File::create(output).map_err(|e| ImageError::Io(e.to_string()))?;
        let encoder = WebPEncoder::new_lossless(file);
        encoder
            .write_image(rgba.as_raw(), rgba.width(), rgba.height(), ColorType::Rgba8.into())
            .map_err(|e| ImageError::Encode(e.to_string()))?;
    } else {
        let encoded = webp::Encoder::from_rgba(rgba.as_raw(), rgba.width(), rgba.height())
            .encode(f32::from(quality));
        fs::write(output, &*encoded).map_err(|e| ImageError::Io(e.to_string()))?;
    }
    Ok(())
}

fn encode_png(img: &DynamicImage, output: &Path) -> Result<(), ImageError> {
    let rgba = img.to_rgba8();
    let file = fs::File::create(output).map_err(|e| ImageError::Io(e.to_string()))?;
    let encoder = PngEncoder::new(file);
    encoder
        .write_image(rgba.as_raw(), rgba.width(), rgba.height(), ColorType::Rgba8.into())
        .map_err(|e| ImageError::Encode(e.to_string()))?;
    Ok(())
}

fn normalize_format(fmt: &str) -> String {
    fmt.trim().to_ascii_lowercase()
}

fn cancelled(should_cancel: Option<&dyn Fn() -> bool>) -> bool {
    should_cancel.is_some_and(|check| check())
}
