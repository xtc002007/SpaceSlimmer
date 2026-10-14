import AVFoundation
import Foundation

/// Platform AAC encoder stub (decode → re-encode to M4A).
enum AacEncoder {
    struct EncodeParams {
        let inputURL: URL
        let outputURL: URL
        let bitrateBps: Int
        let targetChannels: Int
        let isCancelled: () -> Bool
    }

    enum EncodeError: Error, LocalizedError {
        case cancelled
        case noAudioTrack
        case notImplemented

        var errorDescription: String? {
            switch self {
            case .cancelled: return "encode cancelled"
            case .noAudioTrack: return "no audio track in source"
            case .notImplemented: return "AAC encode not implemented (Wave D stub)"
            }
        }
    }

    static func encode(params: EncodeParams) throws -> Int64 {
        if params.isCancelled() {
            throw EncodeError.cancelled
        }

        let asset = AVURLAsset(url: params.inputURL)
        guard asset.tracks(withMediaType: .audio).isEmpty == false else {
            throw EncodeError.noAudioTrack
        }

        throw EncodeError.notImplemented
    }
}
