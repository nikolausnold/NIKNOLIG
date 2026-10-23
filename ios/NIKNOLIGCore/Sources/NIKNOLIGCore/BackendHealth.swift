import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public enum BackendEndpointError: Error, Equatable {
    case invalidHTTPSOrigin
}

public struct BackendEndpoint: Equatable, Sendable {
    public let healthURL: URL

    public init(baseURL: String) throws {
        let value = baseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard var components = URLComponents(string: value),
              components.scheme?.lowercased() == "https",
              let host = components.host, !host.isEmpty,
              components.user == nil, components.password == nil,
              components.port == nil,
              components.percentEncodedPath.isEmpty || components.percentEncodedPath == "/",
              components.query == nil, components.fragment == nil else {
            throw BackendEndpointError.invalidHTTPSOrigin
        }
        components.path = "/v1/health"
        guard let healthURL = components.url else {
            throw BackendEndpointError.invalidHTTPSOrigin
        }
        self.healthURL = healthURL
    }
}

public enum BackendHealthResponse {
    public static func isValid(_ data: Data) -> Bool {
        guard let value = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              value.count == 3,
              value["status"] as? String == "ok",
              value["apiVersion"] as? String == "v1",
              value["mode"] as? String == "sandbox" else {
            return false
        }
        return true
    }
}

private final class BoundedResponseDelegate: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    private let continuation: CheckedContinuation<(Data, URLResponse), Error>
    private let maximumBytes: Int
    private var data = Data()
    private var response: URLResponse?
    private var session: URLSession?
    private var task: URLSessionDataTask?

    init(
        continuation: CheckedContinuation<(Data, URLResponse), Error>,
        maximumBytes: Int
    ) {
        self.continuation = continuation
        self.maximumBytes = maximumBytes
    }

    func start(configuration: URLSessionConfiguration, request: URLRequest) {
        let callbackQueue = OperationQueue()
        callbackQueue.maxConcurrentOperationCount = 1
        session = URLSession(configuration: configuration, delegate: self, delegateQueue: callbackQueue)
        task = session?.dataTask(with: request)
        task?.resume()
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        completionHandler(nil)
    }

    func urlSession(
        _ session: URLSession,
        dataTask: URLSessionDataTask,
        didReceive response: URLResponse,
        completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
    ) {
        self.response = response
        guard response.expectedContentLength <= maximumBytes || response.expectedContentLength < 0 else {
            completionHandler(.cancel)
            return
        }
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive chunk: Data) {
        guard chunk.count <= maximumBytes - data.count else {
            dataTask.cancel()
            return
        }
        data.append(chunk)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if let error {
            continuation.resume(throwing: error)
        } else if let response {
            continuation.resume(returning: (data, response))
        } else {
            continuation.resume(throwing: URLError(.badServerResponse))
        }
        self.task = nil
        self.session?.finishTasksAndInvalidate()
        self.session = nil
    }
}

public final class BackendHealthClient {
    private static let maximumResponseBytes = 4_096
    private let endpoint: BackendEndpoint

    public init(endpoint: BackendEndpoint) {
        self.endpoint = endpoint
    }

    public func checkHealth() async throws -> Bool {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 5
        configuration.timeoutIntervalForResource = 5
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCache = nil
        var request = URLRequest(url: endpoint.healthURL)
        request.httpMethod = "GET"
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await withCheckedThrowingContinuation {
            (continuation: CheckedContinuation<(Data, URLResponse), Error>) in
            BoundedResponseDelegate(
                continuation: continuation,
                maximumBytes: Self.maximumResponseBytes
            ).start(configuration: configuration, request: request)
        }
        guard let response = response as? HTTPURLResponse,
              response.statusCode == 200,
              response.value(forHTTPHeaderField: "Content-Type")?
                .split(separator: ";", maxSplits: 1)
                .first?
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased() == "application/json" else {
            return false
        }
        return BackendHealthResponse.isValid(data)
    }
}
