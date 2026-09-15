import Foundation

// Every type here mirrors one schema in /contracts. Keys are snake_case per
// the contracts. Request types encode optional fields as explicit nulls (like
// the Android app's explicitNulls = true) so both clients send identical JSON.

/// contracts/item.schema.json
struct ItemDto: Codable, Equatable {
    var name: String
    var brand: String? = nil
    var model: String? = nil
    var category: String
    var condition: Condition
    var attributes: [String] = []
    var searchQuery: String
    var confidence: Double
    var notes: String? = nil

    enum CodingKeys: String, CodingKey {
        case name, brand, model, category, condition, attributes
        case searchQuery = "search_query"
        case confidence, notes
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(name, forKey: .name)
        try c.encode(brand, forKey: .brand)
        try c.encode(model, forKey: .model)
        try c.encode(category, forKey: .category)
        try c.encode(condition, forKey: .condition)
        try c.encode(attributes, forKey: .attributes)
        try c.encode(searchQuery, forKey: .searchQuery)
        try c.encode(confidence, forKey: .confidence)
        try c.encode(notes, forKey: .notes)
    }
}

/// contracts/identify.request.schema.json
struct IdentifyRequest: Codable, Equatable {
    static let mediaTypeJPEG = "image/jpeg"
    static let mediaTypePNG = "image/png"

    var imageBase64: String
    var mediaType: String = IdentifyRequest.mediaTypeJPEG
    var hint: String? = nil

    enum CodingKeys: String, CodingKey {
        case imageBase64 = "image_base64"
        case mediaType = "media_type"
        case hint
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(imageBase64, forKey: .imageBase64)
        try c.encode(mediaType, forKey: .mediaType)
        try c.encode(hint, forKey: .hint)
    }
}

/// identify.response.schema.json#/$defs/ListingText
struct ListingText: Codable, Equatable {
    var title: String
    var description: String
}

/// contracts/identify.response.schema.json
struct IdentifyResponse: Codable, Equatable {
    var item: ItemDto
    var listingText: ListingText
    var requestId: String

    enum CodingKeys: String, CodingKey {
        case item
        case listingText = "listing_text"
        case requestId = "request_id"
    }
}

/// contracts/price.request.schema.json
struct PriceRequest: Codable, Equatable {
    static let defaultLocalSaleFactor = 0.85

    var item: ItemDto
    var localSaleFactor: Double = PriceRequest.defaultLocalSaleFactor

    enum CodingKeys: String, CodingKey {
        case item
        case localSaleFactor = "local_sale_factor"
    }
}

/// price.response.schema.json#/$defs/Comp
struct Comp: Codable, Equatable, Identifiable {
    var itemId: String
    var title: String
    var price: Double
    var shipping: Double
    var total: Double
    var condition: String? = nil
    var url: String
    var imageUrl: String? = nil

    var id: String { itemId }

    enum CodingKeys: String, CodingKey {
        case itemId = "item_id"
        case title, price, shipping, total, condition, url
        case imageUrl = "image_url"
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(itemId, forKey: .itemId)
        try c.encode(title, forKey: .title)
        try c.encode(price, forKey: .price)
        try c.encode(shipping, forKey: .shipping)
        try c.encode(total, forKey: .total)
        try c.encode(condition, forKey: .condition)
        try c.encode(url, forKey: .url)
        try c.encode(imageUrl, forKey: .imageUrl)
    }
}

/// price.response.schema.json#/$defs/SoldEstimate "source"
enum SoldEstimateSource: String, Codable, Equatable {
    case llmEstimate = "llm_estimate"
    case ebayMarketplaceInsights = "ebay_marketplace_insights"
    case thirdParty = "third_party"

    /// "ebay marketplace insights", for copy.
    var humanName: String { rawValue.replacingOccurrences(of: "_", with: " ") }
}

/// price.response.schema.json#/$defs/SoldEstimate
struct SoldEstimate: Codable, Equatable {
    var low: Double
    var high: Double
    var currency: String = "USD"
    var source: SoldEstimateSource
    var rationale: String? = nil

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(low, forKey: .low)
        try c.encode(high, forKey: .high)
        try c.encode(currency, forKey: .currency)
        try c.encode(source, forKey: .source)
        try c.encode(rationale, forKey: .rationale)
    }
}

/// contracts/price.response.schema.json (title: PriceQuote)
struct PriceQuote: Codable, Equatable {
    var suggestedPrice: Double?
    var currency: String = "USD"
    var askingMedian: Double?
    var askingLow: Double?
    var askingHigh: Double?
    var compCount: Int
    var conditionFiltered: Bool
    var localSaleFactor: Double
    var comps: [Comp] = []
    var estimatedSold: SoldEstimate?
    var searchQuery: String
    var requestId: String

    enum CodingKeys: String, CodingKey {
        case suggestedPrice = "suggested_price"
        case currency
        case askingMedian = "asking_median"
        case askingLow = "asking_low"
        case askingHigh = "asking_high"
        case compCount = "comp_count"
        case conditionFiltered = "condition_filtered"
        case localSaleFactor = "local_sale_factor"
        case comps
        case estimatedSold = "estimated_sold"
        case searchQuery = "search_query"
        case requestId = "request_id"
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(suggestedPrice, forKey: .suggestedPrice)
        try c.encode(currency, forKey: .currency)
        try c.encode(askingMedian, forKey: .askingMedian)
        try c.encode(askingLow, forKey: .askingLow)
        try c.encode(askingHigh, forKey: .askingHigh)
        try c.encode(compCount, forKey: .compCount)
        try c.encode(conditionFiltered, forKey: .conditionFiltered)
        try c.encode(localSaleFactor, forKey: .localSaleFactor)
        try c.encode(comps, forKey: .comps)
        try c.encode(estimatedSold, forKey: .estimatedSold)
        try c.encode(searchQuery, forKey: .searchQuery)
        try c.encode(requestId, forKey: .requestId)
    }
}

/// bundle.request.schema.json "items[]"
struct BundleRequestItem: Codable, Equatable {
    var item: ItemDto
    var price: Double
}

/// contracts/bundle.request.schema.json
struct BundleRequest: Codable, Equatable {
    var items: [BundleRequestItem]
    var bundlePrice: Double

    enum CodingKeys: String, CodingKey {
        case items
        case bundlePrice = "bundle_price"
    }
}

/// contracts/bundle.response.schema.json
struct BundleResponse: Codable, Equatable {
    var title: String
    var description: String
    var requestId: String

    enum CodingKeys: String, CodingKey {
        case title, description
        case requestId = "request_id"
    }
}

/// contracts/listing.schema.json. On-device today; this is the sync shape.
struct ListingDto: Codable, Equatable {
    var id: String
    var remoteId: String? = nil
    var kind: ListingKind
    var itemIds: [String]
    var title: String
    var description: String
    var price: Double
    var bundleDiscount: Double? = nil
    var status: ListingStatus
    var listedAt: String? = nil
    var soldAt: String? = nil
    var createdAt: String
    var updatedAt: String

    enum CodingKeys: String, CodingKey {
        case id
        case remoteId = "remote_id"
        case kind
        case itemIds = "item_ids"
        case title, description, price
        case bundleDiscount = "bundle_discount"
        case status
        case listedAt = "listed_at"
        case soldAt = "sold_at"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(remoteId, forKey: .remoteId)
        try c.encode(kind, forKey: .kind)
        try c.encode(itemIds, forKey: .itemIds)
        try c.encode(title, forKey: .title)
        try c.encode(description, forKey: .description)
        try c.encode(price, forKey: .price)
        try c.encode(bundleDiscount, forKey: .bundleDiscount)
        try c.encode(status, forKey: .status)
        try c.encode(listedAt, forKey: .listedAt)
        try c.encode(soldAt, forKey: .soldAt)
        try c.encode(createdAt, forKey: .createdAt)
        try c.encode(updatedAt, forKey: .updatedAt)
    }
}
