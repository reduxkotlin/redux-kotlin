package com.github.jetbrains.rssreader.domain

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlOtherAttributes
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

@Serializable
@XmlSerialName("rss", "", "")
data class RssFeed(
    val version: String?,
    var sourceUrl: String = "",
    var isDefault: Boolean = false,
    @XmlSerialName("channel", "", "") val channel: Channel?
)

@Serializable
data class Channel(
    @XmlElement val title: String?,
    @XmlElement val description: String?,
    @XmlElement val link: String?,
    @XmlElement val copyright: String? = null,
    @XmlSerialName("item", "", "") @XmlElement val item: List<Item>,
    @XmlSerialName("image", "", "") val image: Image? = null,

)

@Serializable
data class Image(
    @XmlElement val url: String?,
    @XmlElement val title: String?,
    @XmlElement val link: String?,
    @XmlElement val width: Int?,
    @XmlElement val height: Int?
)

@Serializable
data class Item(
    @XmlElement val title: String?,
    @XmlElement val pubDate: String?,
    @XmlElement val link: String?,
    @XmlElement val guid: String,
    @XmlElement val description: String?,
    @XmlSerialName(
        "encoded",
        "http://purl.org/rss/1.0/modules/content/",
        "content"
    ) @XmlElement val contentEncoded: String?,
    @XmlSerialName(
        "enclosure",
        "http://search.yahoo.com/mrss/",
        "media"
    ) @XmlElement val mediaContent: MediaContent? = null
)

fun Item.getImageUrl(): String? {
    return mediaContent?.url ?: contentEncoded?.let { content ->
        val imgRegex = "<img[^>]+src=\"([^\"]+)\"".toRegex()
        return imgRegex.find(content)?.groupValues?.get(1)
    }
}

@Serializable
data class MediaContent(
    @XmlElement val type: String? = null,
    @XmlElement val url: String? = null,
    @XmlElement val height: String? = null,
    @XmlElement val width: String? = null,
    @XmlSerialName(
        "title",
        "http://search.yahoo.com/mrss/",
        "media"
    ) @XmlElement val mediaTitle: String? = null,
    @XmlSerialName(
        "description",
        "http://search.yahoo.com/mrss/",
        "media"
    ) val mediaDescription: MediaDescription? = null,
    @XmlSerialName(
        "credit",
        "http://search.yahoo.com/mrss/",
        "media"
    ) val mediaCredit: MediaCredit? = null
)

@Serializable
@XmlSerialName("description", "http://search.yahoo.com/mrss/", "media")
data class MediaDescription(
    val type: String? = null,
    @XmlValue val value: String = ""
)

@Serializable
@XmlSerialName("credit", "http://search.yahoo.com/mrss/", "media")
data class MediaCredit(
    val role: String? = null,
    val scheme: String? = null,
    @XmlValue val value: String = ""
)
