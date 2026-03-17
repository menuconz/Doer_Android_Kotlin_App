package nz.co.doer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Base class mirroring the MAUI EntityBase.
 * Common fields sent with most API requests.
 */
@Serializable
open class EntityBase(
    @SerialName("LId") open val lId: Int = 0,
    @SerialName("SiteId") open val siteId: Int = 1,
    @SerialName("GamesId") open val gamesId: Int = 0,
    @SerialName("ContactID") open val contactId: Int = 0,
    @SerialName("UserID") open val userId: String = "",
    @SerialName("ErrorMessage") open val errorMessage: String? = null,
    @SerialName("Status") open val status: Boolean = false,
    @SerialName("BasicAuthUid") open val basicAuthUid: String = ""
)
