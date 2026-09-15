import Foundation

/// The FastAPI backend. Every request resolves the base URL fresh (so a value
/// saved in Settings a moment ago is honoured immediately) and carries the
/// Firebase Bearer token when the provider has one.
final class SnapsellApi {
    private let baseUrlProvider: () -> String
    private let tokenProvider: TokenProvider
    private let session: URLSession

    init(baseUrlProvider: @escaping () -> String, tokenProvider: TokenProvider, session: URLSession? = nil) {
        self.baseUrlProvider = baseUrlProvider
        self.tokenProvider = tokenProvider
        self.session = session ?? {
            let config = URLSessionConfiguration.default
            // Vision-LLM identification and eBay lookups take a while.
            config.timeoutIntervalForRequest = 90
            config.timeoutIntervalForResource = 120
            config.waitsForConnectivity = false
            return URLSession(configuration: config)
        }()
    }

    func health() async throws {
        _ = try await send(path: "health", method: "GET", body: Optional<Data>.none)
    }

    func identify(_ body: IdentifyRequest) async throws -> IdentifyResponse {
        try await post("identify", body)
    }

    func price(_ body: PriceRequest) async throws -> PriceQuote {
        try await post("price", body)
    }

    func bundle(_ body: BundleRequest) async throws -> BundleResponse {
        try await post("bundle", body)
    }

    private func post<Req: Encodable, Res: Decodable>(_ path: String, _ body: Req) async throws -> Res {
        let data = try await send(path: path, method: "POST", body: try SnapsellJSON.encode(body))
        return try SnapsellJSON.decode(Res.self, from: data)
    }

    private func send(path: String, method: String, body: Data?) async throws -> Data {
        guard let url = URL(string: BackendUrl.normalize(baseUrlProvider()) + path) else {
            throw AppError("Bad backend URL. Fix it in Settings.")
        }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.httpBody = body
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if let token = await tokenProvider.idToken(), !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw AppError("No HTTP response from the backend.") }
        guard (200..<300).contains(http.statusCode) else {
            throw HttpError(status: http.statusCode, body: String(data: data, encoding: .utf8) ?? "")
        }
        return data
    }
}
