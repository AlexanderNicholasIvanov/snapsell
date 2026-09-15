import Foundation

/// The one error type the UI shows. Repositories fold every failure into it
/// with a message a person can act on (see `describe(_:)`).
struct AppError: Error, Equatable, LocalizedError {
    let message: String
    var errorDescription: String? { message }

    init(_ message: String) { self.message = message }

    /// The backend answers errors with `{"detail": "..."}`, so that text is
    /// preferred over the bare status.
    static func describe(_ error: Error) -> AppError {
        if let app = error as? AppError { return app }
        if let http = error as? HttpError {
            if let detail = http.detail { return AppError("Server: \(detail) (HTTP \(http.status))") }
            switch http.status {
            case 401, 403: return AppError("Not signed in or not on the allowlist (HTTP \(http.status))")
            case 429: return AppError("Daily limit reached on the server (HTTP 429)")
            default: return AppError("Server error (HTTP \(http.status))")
            }
        }
        if let url = error as? URLError {
            switch url.code {
            case .cannotConnectToHost, .cannotFindHost, .notConnectedToInternet, .dnsLookupFailed, .networkConnectionLost:
                return AppError("Can't reach the backend. Is it running, and is the URL in Settings right?")
            case .timedOut:
                return AppError("The backend took too long to answer.")
            default:
                return AppError("Network error: \(url.localizedDescription)")
            }
        }
        if let decoding = error as? DecodingError {
            return AppError("Unexpected response from the backend: \(decoding.shortDescription)")
        }
        return AppError(error.localizedDescription)
    }
}

/// A non-2xx HTTP response, with the backend's `detail` when it sent one.
struct HttpError: Error {
    let status: Int
    let body: String

    var detail: String? {
        let pattern = #""detail"\s*:\s*"([^"]+)""#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: body, range: NSRange(body.startIndex..., in: body)),
              let range = Range(match.range(at: 1), in: body)
        else { return nil }
        return String(body[range])
    }
}

private extension DecodingError {
    var shortDescription: String {
        switch self {
        case .keyNotFound(let key, _): return "missing key '\(key.stringValue)'"
        case .typeMismatch(let type, let ctx): return "wrong type for '\(ctx.codingPath.map(\.stringValue).joined(separator: "."))' (expected \(type))"
        case .valueNotFound(let type, let ctx): return "null '\(ctx.codingPath.map(\.stringValue).joined(separator: "."))' (expected \(type))"
        case .dataCorrupted(let ctx): return ctx.debugDescription
        @unknown default: return localizedDescription
        }
    }
}

/// Runs `body` and folds any thrown error into `AppError`.
func appResult<T>(_ body: () async throws -> T) async -> Result<T, AppError> {
    do { return .success(try await body()) } catch is CancellationError { return .failure(AppError("Cancelled")) } catch { return .failure(AppError.describe(error)) }
}

extension Result where Failure == AppError {
    var value: Success? { if case .success(let v) = self { return v } else { return nil } }
    var errorMessage: String? { if case .failure(let e) = self { return e.message } else { return nil } }
}
