use std::fs::File;
use std::io::Write;
use std::path::Path;

use ss_domain::Recipe;
use symphonia::core::audio::{AudioBufferRef, Signal};
use symphonia::core::codecs::CODEC_TYPE_NULL;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

use crate::error::AudioError;

#[derive(Clone, Debug)]
pub struct AudioEncodeOptions {
    pub out_format: String,
    pub bitrate_bps: u64,
    pub mono: bool,
}

#[derive(Clone, Debug)]
pub struct AudioProbe {
    pub duration_ms: u64,
    pub sample_rate: u32,
    pub channels: u16,
    pub codec: String,
}

#[derive(Clone, Debug)]
pub struct AudioEncodeResult {
    pub output_bytes: u64,
    pub duration_ms: u64,
}

pub fn options_from_recipe(recipe: &Recipe) -> Result<AudioEncodeOptions, AudioError> {
    let out_format = recipe
        .audio_out_format
        .clone()
        .ok_or(AudioError::InvalidRecipe)?;
    Ok(AudioEncodeOptions {
        out_format,
        bitrate_bps: recipe.audio_bitrate_bps,
        mono: recipe.audio_channels == Some(1),
    })
}

pub fn validate_source(input: &Path) -> Result<AudioProbe, AudioError> {
    probe_audio(input)
}

pub fn transcode_audio(
    input: &Path,
    output: &Path,
    options: &AudioEncodeOptions,
) -> Result<AudioEncodeResult, AudioError> {
    let probe = probe_audio(input)?;
    if options.out_format.eq_ignore_ascii_case("opus") {
        #[cfg(feature = "opus-encode")]
        {
            return transcode_to_opus_ogg(input, output, options, &probe);
        }
        #[cfg(not(feature = "opus-encode"))]
        {
            return Err(AudioError::Unsupported(
                "opus output requires opus-encode feature".into(),
            ));
        }
    }
    Err(AudioError::Unsupported(format!(
        "format {} requires platform encoder",
        options.out_format
    )))
}

#[cfg(feature = "opus-encode")]
fn transcode_to_opus_ogg(
    input: &Path,
    output: &Path,
    options: &AudioEncodeOptions,
    probe: &AudioProbe,
) -> Result<AudioEncodeResult, AudioError> {
    use opus::{Application, Channels, Encoder};
    use ogg::{PacketWriter, PacketWriteEndInfo};

    let pcm = decode_to_pcm(input)?;
    let channels = if options.mono { 1 } else { pcm.channels.min(2) };
    let sample_rate = pcm.sample_rate;
    let mut encoder = Encoder::new(
        sample_rate,
        if channels == 1 {
            Channels::Mono
        } else {
            Channels::Stereo
        },
        Application::Audio,
    )
    .map_err(|e| AudioError::Encode(e.to_string()))?;
    let bitrate = options.bitrate_bps.min(256_000) as i32;
    encoder
        .set_bitrate(opus::Bitrate::Bits(bitrate))
        .map_err(|e| AudioError::Encode(e.to_string()))?;

    let mut interleaved = interleave_pcm(&pcm.samples, pcm.channels, channels);
    let frame_size = sample_rate as usize / 50;
    let mut file = File::create(output).map_err(|e| AudioError::Io(e.to_string()))?;
    let mut writer = PacketWriter::new(file);
    let serial = 0x534C_494D_u32;
    let mut stream = writer
        .new_stream(serial, 2)
        .map_err(|e| AudioError::Encode(e.to_string()))?;

    let id_header = build_opus_id_header(channels as u8, sample_rate);
    stream
        .packet_write(
            &id_header,
            0,
            PacketWriteEndInfo::EndPage,
            0,
        )
        .map_err(|e| AudioError::Encode(e.to_string()))?;

    let comment_header = build_opus_comment_header();
    stream
        .packet_write(
            &comment_header,
            1,
            PacketWriteEndInfo::EndPage,
            0,
        )
        .map_err(|e| AudioError::Encode(e.to_string()))?;

    let mut pos = 0usize;
    let mut page_index = 2u64;
    while pos < interleaved.len() {
        let end = (pos + frame_size * channels).min(interleaved.len());
        let frame = &interleaved[pos..end];
        if frame.is_empty() {
            break;
        }
        let mut out = vec![0u8; 4000];
        let len = encoder
            .encode(frame, &mut out)
            .map_err(|e| AudioError::Encode(e.to_string()))?;
        stream
            .packet_write(
                &out[..len],
                page_index,
                PacketWriteEndInfo::EndPage,
                ((pos / channels) as u64 * 48_000) / sample_rate as u64,
            )
            .map_err(|e| AudioError::Encode(e.to_string()))?;
        page_index += 1;
        pos = end;
    }

    let mut tail = vec![0u8; 4000];
    let len = encoder
        .flush(&mut tail)
        .map_err(|e| AudioError::Encode(e.to_string()))?;
    if len > 0 {
        stream
            .packet_write(&tail[..len], page_index, PacketWriteEndInfo::EndStream, 0)
            .map_err(|e| AudioError::Encode(e.to_string()))?;
    }

    let output_bytes = std::fs::metadata(output)
        .map_err(|e| AudioError::Io(e.to_string()))?
        .len();
    Ok(AudioEncodeResult {
        output_bytes,
        duration_ms: probe.duration_ms,
    })
}

#[cfg(feature = "opus-encode")]
struct PcmData {
    samples: Vec<i16>,
    channels: usize,
    sample_rate: u32,
}

#[cfg(feature = "opus-encode")]
fn decode_to_pcm(input: &Path) -> Result<PcmData, AudioError> {
    use symphonia::core::codecs::DecoderOptions;
    use symphonia::core::errors::Error;

    let src = File::open(input).map_err(|e| AudioError::Io(e.to_string()))?;
    let mss = MediaSourceStream::new(Box::new(src), Default::default());
    let probed = symphonia::default::get_probe()
        .format(&Hint::new(), mss, &FormatOptions::default(), &MetadataOptions::default())
        .map_err(|e| AudioError::Decode(e.to_string()))?;
    let mut format = probed.format;
    let track = format
        .default_track()
        .ok_or_else(|| AudioError::Decode("no default track".into()))?;
    let sample_rate = track
        .codec_params
        .sample_rate
        .ok_or_else(|| AudioError::Decode("missing sample rate".into()))?;
    let channels = track
        .codec_params
        .channels
        .map(|c| c.count())
        .unwrap_or(2);
    let mut decoder = symphonia::default::get_codecs()
        .make(&track.codec_params, &DecoderOptions::default())
        .map_err(|e| AudioError::Decode(e.to_string()))?;
    let mut samples = Vec::new();
    while !format.metadata().is_latest_revision() {
        format.metadata().reset();
    }
    loop {
        let packet = match format.next_packet() {
            Ok(p) => p,
            Err(Error::ResetRequired) => break,
            Err(Error::IoError(_)) => break,
            Err(e) => return Err(AudioError::Decode(e.to_string())),
        };
        if packet.track_id() != track.id {
            continue;
        }
        let decoded = decoder
            .decode(&packet)
            .map_err(|e| AudioError::Decode(e.to_string()))?;
        append_decoded_samples(decoded, &mut samples);
    }
    Ok(PcmData {
        samples,
        channels,
        sample_rate,
    })
}

#[cfg(feature = "opus-encode")]
fn append_decoded_samples(decoded: AudioBufferRef, out: &mut Vec<i16>) {
    match decoded {
        AudioBufferRef::F32(buf) => {
            let channels = buf.spec().channels.count();
            for i in 0..buf.frames() {
                for ch in 0..channels {
                    let s = buf.chan(ch)[i].clamp(-1.0, 1.0);
                    out.push((s * 32767.0) as i16);
                }
            }
        }
        AudioBufferRef::S16(buf) => {
            let channels = buf.spec().channels.count();
            for i in 0..buf.frames() {
                for ch in 0..channels {
                    out.push(buf.chan(ch)[i]);
                }
            }
        }
        _ => {}
    }
}

#[cfg(feature = "opus-encode")]
fn interleave_pcm(samples: &[i16], src_channels: usize, dst_channels: usize) -> Vec<i16> {
    if src_channels == 0 {
        return Vec::new();
    }
    let frames = samples.len() / src_channels;
    let mut out = Vec::with_capacity(frames * dst_channels);
    for frame in 0..frames {
        if dst_channels == 1 {
            let mut sum = 0i32;
            for ch in 0..src_channels {
                sum += samples[frame * src_channels + ch] as i32;
            }
            out.push((sum / src_channels as i32) as i16);
        } else {
            let left = samples[frame * src_channels];
            let right = if src_channels > 1 {
                samples[frame * src_channels + 1]
            } else {
                left
            };
            out.push(left);
            out.push(right);
        }
    }
    out
}

#[cfg(feature = "opus-encode")]
fn build_opus_id_header(channels: u8, sample_rate: u32) -> Vec<u8> {
    let mut header = vec![0u8; 19];
    header[..8].copy_from_slice(b"OpusHead");
    header[8] = 1;
    header[9] = channels;
    header[10..12].copy_from_slice(&0u16.to_le_bytes());
    header[12..16].copy_from_slice(&sample_rate.to_le_bytes());
    header[16..18].copy_from_slice(&0u16.to_le_bytes());
    header[18] = 0;
    header
}

#[cfg(feature = "opus-encode")]
fn build_opus_comment_header() -> Vec<u8> {
    let vendor = b"SpaceSlimmer";
    let mut header = Vec::new();
    header.extend_from_slice(b"OpusTags");
    header.extend_from_slice(&(vendor.len() as u32).to_le_bytes());
    header.extend_from_slice(vendor);
    header.extend_from_slice(&0u32.to_le_bytes());
    header
}

pub fn probe_audio(input: &Path) -> Result<AudioProbe, AudioError> {
    let src = File::open(input).map_err(|e| AudioError::Io(e.to_string()))?;
    let mss = MediaSourceStream::new(Box::new(src), Default::default());
    let hint = Hint::new();
    let probed = symphonia::default::get_probe()
        .format(&hint, mss, &FormatOptions::default(), &MetadataOptions::default())
        .map_err(|e| AudioError::Decode(e.to_string()))?;
    let mut format = probed.format;
    let track = format
        .tracks()
        .iter()
        .find(|t| t.codec_params.codec != CODEC_TYPE_NULL)
        .ok_or_else(|| AudioError::Decode("no audio track".into()))?;
    let sample_rate = track.codec_params.sample_rate.unwrap_or(48_000);
    let channels = track.codec_params.channels.map(|c| c.count()).unwrap_or(2) as u16;
    let duration_ms = track
        .codec_params
        .n_frames
        .map(|f| (f as u64 * 1000) / sample_rate as u64)
        .unwrap_or_else(|| {
            if let Some(time_base) = track.codec_params.time_base {
                if let Some(n_frames) = track.codec_params.n_frames {
                    return (n_frames as f64 * time_base.numer as f64 * 1000.0 / time_base.denom as f64)
                        as u64;
                }
            }
            0
        });
    let codec = format!("{:?}", track.codec_params.codec).to_ascii_lowercase();
    Ok(AudioProbe {
        duration_ms,
        sample_rate,
        channels,
        codec,
    })
}
