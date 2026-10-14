//! Media probe helpers. L2 reads container headers without full decode.

/// Probe crate identity for wiring tests.
pub fn crate_name() -> &'static str {
    "ss-probe"
}

/// Dimensions read from a JPEG Start Of Frame marker.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct JpegDimensions {
    pub width: u32,
    pub height: u32,
}

/// Dimensions read from a PNG IHDR chunk.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct PngDimensions {
    pub width: u32,
    pub height: u32,
}

/// Basic MP4 `ftyp` brand string if present near file start.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Mp4Ftyp {
    pub major_brand: String,
    pub compatible_brands: Vec<String>,
}

/// L2 probe: JPEG SOF dimensions (no full decode).
pub fn probe_jpeg_dimensions(bytes: &[u8]) -> Option<JpegDimensions> {
    if bytes.len() < 4 || bytes[0] != 0xFF || bytes[1] != 0xD8 {
        return None;
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
        if matches!(marker, 0xC0..=0xCF) && marker != 0xC4 && marker != 0xC8 && marker != 0xCC {
            if len >= 7 {
                let height = u16::from_be_bytes([bytes[i + 5], bytes[i + 6]]) as u32;
                let width = u16::from_be_bytes([bytes[i + 7], bytes[i + 8]]) as u32;
                return Some(JpegDimensions { width, height });
            }
        }
        i += 2 + len;
    }
    None
}

/// L2 probe: PNG IHDR dimensions.
pub fn probe_png_dimensions(bytes: &[u8]) -> Option<PngDimensions> {
    const SIG: [u8; 8] = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A];
    if bytes.len() < 24 || bytes[..8] != SIG {
        return None;
    }
    if &bytes[12..16] != b"IHDR" {
        return None;
    }
    let width = u32::from_be_bytes([bytes[16], bytes[17], bytes[18], bytes[19]]);
    let height = u32::from_be_bytes([bytes[20], bytes[21], bytes[22], bytes[23]]);
    Some(PngDimensions { width, height })
}

/// L2 probe: MP4 `ftyp` box near the start of the file.
pub fn probe_mp4_ftyp(bytes: &[u8]) -> Option<Mp4Ftyp> {
    if bytes.len() < 16 {
        return None;
    }
    let mut offset = 0usize;
    while offset + 8 <= bytes.len() {
        let size = u32::from_be_bytes([
            bytes[offset],
            bytes[offset + 1],
            bytes[offset + 2],
            bytes[offset + 3],
        ]) as usize;
        if size < 8 || offset + size > bytes.len() {
            break;
        }
        let box_type = &bytes[offset + 4..offset + 8];
        if box_type == b"ftyp" && size >= 16 {
            let major = String::from_utf8_lossy(&bytes[offset + 8..offset + 12])
                .trim()
                .to_string();
            let mut compatible = Vec::new();
            let mut brand_off = offset + 16;
            while brand_off + 4 <= offset + size {
                compatible.push(
                    String::from_utf8_lossy(&bytes[brand_off..brand_off + 4])
                        .trim()
                        .to_string(),
                );
                brand_off += 4;
            }
            return Some(Mp4Ftyp {
                major_brand: major,
                compatible_brands: compatible,
            });
        }
        if size == 0 {
            break;
        }
        offset += size;
    }
    None
}

/// L2 probe: first video-track `tkhd` width/height (16.16 fixed).
pub fn probe_mp4_video_dimensions(bytes: &[u8]) -> Option<(u32, u32)> {
    let needle = b"tkhd";
    let mut idx = 0usize;
    while idx + 88 <= bytes.len() {
        let at = find_bytes(bytes, needle, idx)?;
        if at < 4 {
            return None;
        }
        let atom_start = at - 4;
        let version = bytes.get(at + 4).copied().unwrap_or(0);
        let dim_offset = if version == 0 {
            atom_start + 76
        } else {
            atom_start + 88
        };
        if dim_offset + 8 <= bytes.len() {
            let w = u32::from_be_bytes(bytes[dim_offset..dim_offset + 4].try_into().ok()?) >> 16;
            let h = u32::from_be_bytes(bytes[dim_offset + 4..dim_offset + 8].try_into().ok()?) >> 16;
            if w > 0 && h > 0 {
                return Some((w, h));
            }
        }
        idx = at + 4;
    }
    None
}

fn find_bytes(haystack: &[u8], needle: &[u8], from: usize) -> Option<usize> {
    if needle.is_empty() || from >= haystack.len() {
        return None;
    }
    haystack[from..]
        .windows(needle.len())
        .position(|w| w == needle)
        .map(|p| from + p)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn crate_name_matches() {
        assert_eq!(super::crate_name(), "ss-probe");
    }

    #[test]
    fn reads_png_ihdr() {
        let mut png = vec![
            0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
        ];
        png.extend_from_slice(b"IHDR");
        png.extend_from_slice(&100u32.to_be_bytes());
        png.extend_from_slice(&50u32.to_be_bytes());
        let dims = probe_png_dimensions(&png).expect("ihdr");
        assert_eq!(dims.width, 100);
        assert_eq!(dims.height, 50);
    }

    #[test]
    fn reads_mp4_ftyp() {
        let mut bytes = Vec::new();
        bytes.extend_from_slice(&20u32.to_be_bytes());
        bytes.extend_from_slice(b"ftyp");
        bytes.extend_from_slice(b"isom");
        bytes.extend_from_slice(&0u32.to_be_bytes());
        bytes.extend_from_slice(b"mp41");
        let ftyp = probe_mp4_ftyp(&bytes).expect("ftyp");
        assert_eq!(ftyp.major_brand, "isom");
        assert!(ftyp.compatible_brands.contains(&"mp41".to_string()));
    }

    #[test]
    fn reads_mp4_tkhd_dimensions() {
        let mut bytes = vec![0u8; 96];
        bytes[4..8].copy_from_slice(b"tkhd");
        bytes[4 + 4] = 0;
        bytes[76..80].copy_from_slice(&(1920u32 << 16).to_be_bytes());
        bytes[80..84].copy_from_slice(&(1080u32 << 16).to_be_bytes());
        let (w, h) = probe_mp4_video_dimensions(&bytes).expect("tkhd");
        assert_eq!(w, 1920);
        assert_eq!(h, 1080);
    }
}
