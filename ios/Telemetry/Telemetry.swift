import Foundation
import os.log

/// Client-side telemetry stubs (no PII). See docs/02-technical-architecture.md §14.
enum Telemetry {
    private static let log = Logger(subsystem: "com.spaceslimmer.ios", category: "telemetry")

    static func probeDone(
        type: String,
        codecFamily: String,
        width: Int,
        height: Int,
        durationBucket: String
    ) {
        emit(
            name: "probe_done",
            fields: [
                "type": type,
                "codec_family": codecFamily,
                "w": String(width),
                "h": String(height),
                "duration_bucket": durationBucket,
            ]
        )
    }

    static func jobFinished(
        result: String,
        preset: String,
        inBucket: String,
        outBucket: String,
        ratio: Double,
        durationMs: Int,
        hardwareAccelerated: Bool
    ) {
        emit(
            name: "job_finished",
            fields: [
                "result": result,
                "preset": preset,
                "in_bucket": inBucket,
                "out_bucket": outBucket,
                "ratio": String(format: "%.3f", ratio),
                "ms": String(durationMs),
                "hw": hardwareAccelerated ? "true" : "false",
            ]
        )
    }

    private static func emit(name: String, fields: [String: String]) {
        let payload = fields
            .sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value)" }
            .joined(separator: " ")
        log.info("\(name, privacy: .public) \(payload, privacy: .public)")
    }
}
