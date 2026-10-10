package com.membora.app.core.media

/** The supported Membora media categories and their access policy. */
enum class MediaType(val privateFile: Boolean) {
    ORGANIZATION_LOGO(false),
    MEMBER_PHOTO(false),
    MEMBER_DOCUMENT(true)
}
