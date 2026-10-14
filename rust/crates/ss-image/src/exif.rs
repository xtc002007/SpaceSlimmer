use std::fs;
use std::fs::File;
use std::io::BufReader;
use std::path::Path;

use exif::{In, Reader, Tag};
use image::imageops;
use image::DynamicImage;

use crate::error::ImageError;

pub fn apply_exif_orientation(img: DynamicImage, path: &Path) -> DynamicImage {
    let orientation = read_orientation(path).unwrap_or(1);
    apply_orientation(img, orientation)
}

fn read_orientation(path: &Path) -> Option<u16> {
    let file = File::open(path).ok()?;
    let mut bufreader = BufReader::new(file);
    let exif = Reader::new().read_from_container(&mut bufreader).ok()?;
    exif.get_field(Tag::Orientation, In::PRIMARY)
        .and_then(|f| f.value.get_uint(0))
        .map(|v| v as u16)
}

fn apply_orientation(img: DynamicImage, orientation: u16) -> DynamicImage {
    match orientation {
        1 => img,
        2 => DynamicImage::ImageRgba8(imageops::flip_horizontal(&img.to_rgba8())),
        3 => DynamicImage::ImageRgba8(imageops::rotate180(&img.to_rgba8())),
        4 => DynamicImage::ImageRgba8(imageops::flip_vertical(&img.to_rgba8())),
        5 => DynamicImage::ImageRgba8(imageops::flip_horizontal(&imageops::rotate270(&img.to_rgba8()))),
        6 => DynamicImage::ImageRgba8(imageops::rotate90(&img.to_rgba8())),
        7 => DynamicImage::ImageRgba8(imageops::flip_horizontal(&imageops::rotate90(&img.to_rgba8()))),
        8 => DynamicImage::ImageRgba8(imageops::rotate270(&img.to_rgba8())),
        _ => img,
    }
}

/// Copies EXIF metadata from source to destination JPEG.
/// `DateTimeOriginal` is always preserved; GPS, Make, and Model are preserved when `keep_gps` is true.
pub fn copy_exif_metadata(src: &Path, dst: &Path, keep_gps: bool) -> Result<(), ImageError> {
    let src_bytes = fs::read(src).map_err(|e| ImageError::Io(e.to_string()))?;
    let dst_bytes = fs::read(dst).map_err(|e| ImageError::Io(e.to_string()))?;
    let app1 = extract_app1_segment(&src_bytes)?;
    if app1.is_empty() {
        return Ok(());
    }
    let app1 = if keep_gps {
        app1
    } else {
        strip_gps_ifd_from_app1(&app1)
    };
    let app1 = reset_orientation_in_app1(&app1);
    let merged = inject_app1_into_jpeg(&dst_bytes, &app1)?;
    fs::write(dst, merged).map_err(|e| ImageError::Io(e.to_string()))?;
    Ok(())
}

/// Copies the APP1 (Exif) segment from a source JPEG into a freshly encoded JPEG output.
pub fn copy_exif_app1(src: &Path, dst: &Path) -> Result<(), ImageError> {
    copy_exif_metadata(src, dst, true)
}

fn extract_app1_segment(bytes: &[u8]) -> Result<Vec<u8>, ImageError> {
    if bytes.len() < 4 || bytes[0] != 0xFF || bytes[1] != 0xD8 {
        return Ok(Vec::new());
    }
    let mut i = 2usize;
    while i + 4 < bytes.len() {
        if bytes[i] != 0xFF {
            break;
        }
        let marker = bytes[i + 1];
        if marker == 0xDA {
            break;
        }
        let len = u16::from_be_bytes([bytes[i + 2], bytes[i + 3]]) as usize;
        if len < 2 || i + 2 + len > bytes.len() {
            break;
        }
        if marker == 0xE1 {
            return Ok(bytes[i..i + 2 + len].to_vec());
        }
        i += 2 + len;
    }
    Ok(Vec::new())
}

/// Removes the GPS IFD pointer (tag 0x8825) from the TIFF header inside APP1.
/// Sets EXIF orientation (tag 0x0112) to 1 after pixels were physically rotated.
fn reset_orientation_in_app1(app1: &[u8]) -> Vec<u8> {
    if app1.len() < 14 {
        return app1.to_vec();
    }
    let mut out = app1.to_vec();
    let tiff_start = 10usize;
    if out.len() <= tiff_start + 8 {
        return out;
    }
    let le = &out[tiff_start..tiff_start + 2] == b"II";
    let ifd_off = tiff_start + 4;
    if out.len() < ifd_off + 2 {
        return out;
    }
    let entry_count = read_u16_at(&out, ifd_off, le) as usize;
    let entries_start = ifd_off + 2;
    for n in 0..entry_count {
        let entry_off = entries_start + n * 12;
        if entry_off + 12 > out.len() {
            break;
        }
        let tag = read_u16_at(&out, entry_off, le);
        if tag == 0x0112 {
            if le {
                out[entry_off + 8] = 1;
                out[entry_off + 9] = 0;
            } else {
                out[entry_off + 8] = 0;
                out[entry_off + 9] = 1;
            }
            out[entry_off + 10] = 0;
            out[entry_off + 11] = 0;
            break;
        }
    }
    out
}

fn strip_gps_ifd_from_app1(app1: &[u8]) -> Vec<u8> {
    if app1.len() < 14 {
        return app1.to_vec();
    }
    let mut out = app1.to_vec();
    let tiff_start = 10usize;
    if out.len() <= tiff_start + 8 {
        return out;
    }
    let le = &out[tiff_start..tiff_start + 2] == b"II";
    let ifd_off = tiff_start + 4;
    if out.len() < ifd_off + 2 {
        return out;
    }
    let entry_count = read_u16_at(&out, ifd_off, le) as usize;
    let entries_start = ifd_off + 2;
    for n in 0..entry_count {
        let entry_off = entries_start + n * 12;
        if entry_off + 12 > out.len() {
            break;
        }
        let tag = read_u16_at(&out, entry_off, le);
        // GPS IFD pointer, Make, Model (P2 privacy strip when GPS is off)
        if tag == 0x8825 || tag == 0x010F || tag == 0x0110 {
            for b in 0..12 {
                out[entry_off + b] = 0;
            }
        }
    }
    out
}

fn read_u16_at(bytes: &[u8], off: usize, le: bool) -> u16 {
    if le {
        u16::from_le_bytes([bytes[off], bytes[off + 1]])
    } else {
        u16::from_be_bytes([bytes[off], bytes[off + 1]])
    }
}

fn inject_app1_into_jpeg(dst: &[u8], app1: &[u8]) -> Result<Vec<u8>, ImageError> {
    if dst.len() < 2 || dst[0] != 0xFF || dst[1] != 0xD8 {
        return Err(ImageError::Encode("output is not a JPEG".into()));
    }
    let mut out = Vec::with_capacity(dst.len() + app1.len());
    out.extend_from_slice(&dst[0..2]);
    out.extend_from_slice(app1);
    let mut i = 2usize;
    while i + 4 < dst.len() {
        if dst[i] != 0xFF {
            out.extend_from_slice(&dst[i..]);
            return Ok(out);
        }
        let marker = dst[i + 1];
        if marker == 0xE1 {
            let len = u16::from_be_bytes([dst[i + 2], dst[i + 3]]) as usize;
            i += 2 + len;
            continue;
        }
        if marker == 0xDA {
            out.extend_from_slice(&dst[i..]);
            return Ok(out);
        }
        let len = u16::from_be_bytes([dst[i + 2], dst[i + 3]]) as usize;
        if len < 2 || i + 2 + len > dst.len() {
            out.extend_from_slice(&dst[i..]);
            return Ok(out);
        }
        out.extend_from_slice(&dst[i..i + 2 + len]);
        i += 2 + len;
    }
    Ok(out)
}

#[cfg(test)]
pub(crate) fn build_app1_exif_segment(datetime: &str, include_gps: bool) -> Result<Vec<u8>, ImageError> {
    use exif::experimental::Writer;
    use exif::{Field, In, Tag, Value};

    assert_eq!(datetime.len(), 19, "datetime must be YYYY:MM:DD HH:MM:SS");
    let datetime_field = Field {
        tag: Tag::DateTimeOriginal,
        ifd_num: In::PRIMARY,
        value: Value::Ascii(vec![datetime.as_bytes().to_vec()]),
    };
    let gps_field = Field {
        tag: Tag::GPSVersionID,
        ifd_num: In::PRIMARY,
        value: Value::Byte(vec![2, 2, 0, 0]),
    };
    let mut writer = Writer::new();
    writer.push_field(&datetime_field);
    if include_gps {
        writer.push_field(&gps_field);
    }
    let mut tiff = Vec::new();
    writer
        .write(&mut std::io::Cursor::new(&mut tiff), false)
        .map_err(|e| ImageError::Encode(e.to_string()))?;

    let payload_len = 2 + 6 + tiff.len();
    let mut app1 = Vec::with_capacity(2 + 2 + 6 + tiff.len());
    app1.extend_from_slice(&[0xFF, 0xE1]);
    app1.extend_from_slice(&(payload_len as u16).to_be_bytes());
    app1.extend_from_slice(b"Exif\0\0");
    app1.extend_from_slice(&tiff);
    Ok(app1)
}

#[cfg(test)]
pub(crate) fn inject_exif_app1_into_jpeg(jpeg: &[u8], app1: &[u8]) -> Result<Vec<u8>, ImageError> {
    inject_app1_into_jpeg(jpeg, app1)
}

#[cfg(test)]
pub(crate) fn read_exif_field(path: &Path, tag: Tag, ifd: In) -> Option<String> {
    let file = File::open(path).ok()?;
    let mut bufreader = BufReader::new(file);
    let exif = Reader::new().read_from_container(&mut bufreader).ok()?;
    exif.get_field(tag, ifd).map(|f| f.display_value().with_unit(&exif).to_string())
}

