//! Software image compression for SpaceSlimmer (Sprint 3).

mod encode;
mod error;
mod exif;

pub use encode::{
    compress_image, options_from_recipe, ImageEncodeOptions, ImageEncodeResult,
};
pub use error::ImageError;

#[cfg(test)]
mod tests {
    use image::{ImageBuffer, ImageEncoder, Rgb};
    use ss_domain::{MediaKind, Recipe};
    use ::exif::{In, Tag};

    use super::*;

    fn sample_recipe() -> Recipe {
        Recipe {
            kind: MediaKind::Image,
            out_w: 640,
            out_h: 480,
            fps: None,
            video_codec: String::new(),
            video_bitrate_bps: 0,
            audio_copy: false,
            audio_codec: String::new(),
            audio_bitrate_bps: 0,
            container: String::new(),
            image_out_format: Some("jpeg".into()),
            image_quality: Some(80),
            max_side: Some(1280),
            audio_out_format: None,
            audio_channels: None,
            trim_start_ms: None,
            trim_end_ms: None,
            target_bytes: None,
        }
    }

    #[test]
    fn jpeg_round_trip_reduces_or_matches_large_input() {
        let dir = std::env::temp_dir().join(format!("ss-image-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).expect("tmpdir");
        let input = dir.join("input.png");
        let output = dir.join("output.jpg");

        let img: ImageBuffer<Rgb<u8>, Vec<u8>> = ImageBuffer::from_fn(2000, 1500, |x, y| {
            Rgb([
                ((x * 255) / 2000) as u8,
                ((y * 255) / 1500) as u8,
                128,
            ])
        });
        img.save(&input).expect("save input");

        let input_bytes = std::fs::metadata(&input).expect("meta").len();
        let options = options_from_recipe(&sample_recipe()).expect("options");
        let result = compress_image(&input, &output, &options, None).expect("compress");
        assert!(output.exists());
        assert!(result.out_width <= 1280);
        assert!(result.out_height <= 1280);
        assert!(result.output_bytes < input_bytes);
        let _ = std::fs::remove_dir_all(dir);
    }

    #[test]
    fn compress_image_honours_cancel_callback() {
        let dir = std::env::temp_dir().join(format!("ss-image-cancel-{}", std::process::id()));
        std::fs::create_dir_all(&dir).expect("tmpdir");
        let input = dir.join("input.png");
        let output = dir.join("output.jpg");
        let img: ImageBuffer<Rgb<u8>, Vec<u8>> = ImageBuffer::from_fn(400, 300, |x, y| {
            Rgb([((x * 255) / 400) as u8, ((y * 255) / 300) as u8, 64])
        });
        img.save(&input).expect("save input");
        let options = options_from_recipe(&sample_recipe()).expect("options");
        let cancelled = std::sync::atomic::AtomicBool::new(true);
        let err = compress_image(
            &input,
            &output,
            &options,
            Some(&|| cancelled.load(std::sync::atomic::Ordering::Relaxed)),
        )
        .expect_err("should cancel");
        assert!(matches!(err, ImageError::Cancelled));
        let _ = std::fs::remove_dir_all(dir);
    }

    #[test]
    fn exif_preserves_datetime_without_gps() {
        let dir = std::env::temp_dir().join(format!("ss-image-exif-{}", std::process::id()));
        std::fs::create_dir_all(&dir).expect("tmpdir");
        let input = dir.join("input.jpg");
        let output = dir.join("output.jpg");

        let img: ImageBuffer<Rgb<u8>, Vec<u8>> = ImageBuffer::from_fn(64, 48, |x, y| {
            Rgb([((x * 255) / 64) as u8, ((y * 255) / 48) as u8, 96])
        });
        let file = std::fs::File::create(&input).expect("create input");
        let encoder = image::codecs::jpeg::JpegEncoder::new_with_quality(file, 90);
        encoder
            .write_image(img.as_raw(), 64, 48, image::ColorType::Rgb8.into())
            .expect("encode base jpeg");

        let datetime = "2024:01:15 10:30:00";
        let base = std::fs::read(&input).expect("read base");
        let app1 = exif::build_app1_exif_segment(datetime, true).expect("build app1");
        let with_exif = exif::inject_exif_app1_into_jpeg(&base, &app1).expect("inject exif");
        std::fs::write(&input, with_exif).expect("write input exif");

        assert!(
            exif::read_exif_field(&input, Tag::DateTimeOriginal, In::PRIMARY)
                .is_some()
        );
        assert!(
            exif::read_exif_field(&input, Tag::GPSVersionID, In::PRIMARY).is_some()
        );

        let mut options = options_from_recipe(&sample_recipe()).expect("options");
        options.keep_gps = false;
        compress_image(&input, &output, &options, None).expect("compress");

        let out_bytes = std::fs::read(&output).expect("read output");
        assert!(
            out_bytes
                .windows(datetime.len())
                .any(|w| w == datetime.as_bytes()),
            "DateTimeOriginal bytes missing from output EXIF"
        );
        // GPS IFD pointer is stripped when keep_gps=false (orphaned GPS payload may remain).
        assert!(
            exif::read_exif_field(&output, Tag::GPSInfoIFDPointer, In::PRIMARY).is_none()
        );

        let _ = std::fs::remove_dir_all(dir);
    }
}
