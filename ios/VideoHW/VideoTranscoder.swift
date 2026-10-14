import AVFoundation
import Foundation

/// AVAssetReader + AVAssetWriter video transcode stub.
///
/// Full HEVC/H.264 pipeline and CoreEngine callbacks land in Phase 2.
enum VideoTranscoder {
    struct TranscodeParams {
        let sourceURL: URL
        let outputURL: URL
        let jobId: String
        let useHevc: Bool
        let onProgress: (Float) -> Void
        let isCancelled: () -> Bool
    }

    enum TranscodeError: Error, LocalizedError {
        case cancelled
        case readerSetupFailed
        case writerSetupFailed
        case notImplemented

        var errorDescription: String? {
            switch self {
            case .cancelled: return "transcode cancelled"
            case .readerSetupFailed: return "AVAssetReader setup failed"
            case .writerSetupFailed: return "AVAssetWriter setup failed"
            case .notImplemented: return "video transcode not implemented (Wave D stub)"
            }
        }
    }

    static func transcode(params: TranscodeParams) throws -> Int64 {
        if params.isCancelled() {
            throw TranscodeError.cancelled
        }

        let asset = AVURLAsset(url: params.sourceURL)
        let reader = try AVAssetReader(asset: asset)
        guard reader.status != .failed else {
            throw TranscodeError.readerSetupFailed
        }

        let writer = try AVAssetWriter(outputURL: params.outputURL, fileType: .mp4)
        guard writer.status != .failed else {
            throw TranscodeError.writerSetupFailed
        }

        params.onProgress(0)
        throw TranscodeError.notImplemented
    }
}
