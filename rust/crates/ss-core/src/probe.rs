//! L2 probe helpers exposed through UniFFI.

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct ImageDimensions {
    pub width: u32,
    pub height: u32,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct Mp4FtypView {
    pub major_brand: String,
    pub compatible_brands: Vec<String>,
}

/// L2 JPEG/PNG dimension probe without full decode.
#[uniffi::export]
pub fn probe_image_dimensions_l2(bytes: Vec<u8>) -> Option<ImageDimensions> {
    if let Some(d) = ss_probe::probe_jpeg_dimensions(&bytes) {
        return Some(ImageDimensions {
            width: d.width,
            height: d.height,
        });
    }
    if let Some(d) = ss_probe::probe_png_dimensions(&bytes) {
        return Some(ImageDimensions {
            width: d.width,
            height: d.height,
        });
    }
    None
}

/// L2 MP4 `ftyp` brand probe from file header bytes.
#[uniffi::export]
pub fn probe_mp4_ftyp_l2(bytes: Vec<u8>) -> Option<Mp4FtypView> {
    ss_probe::probe_mp4_ftyp(&bytes).map(|ftyp| Mp4FtypView {
        major_brand: ftyp.major_brand,
        compatible_brands: ftyp.compatible_brands,
    })
}

/// L2 MP4 `tkhd` video dimensions from header bytes.
#[uniffi::export]
pub fn probe_mp4_video_dimensions_l2(bytes: Vec<u8>) -> Option<ImageDimensions> {
    ss_probe::probe_mp4_video_dimensions(&bytes).map(|(width, height)| ImageDimensions { width, height })
}

/// Lower video bitrate for a second encode when output exceeds the target by 15%.
#[uniffi::export]
pub fn second_pass_video_bitrate(
    current_bps: u64,
    output_bytes: u64,
    target_bytes: u64,
    audio_bytes: u64,
    duration_ms: u64,
) -> Option<u64> {
    ss_domain::second_pass_video_bitrate(
        current_bps,
        output_bytes,
        target_bytes,
        audio_bytes,
        duration_ms,
    )
}
