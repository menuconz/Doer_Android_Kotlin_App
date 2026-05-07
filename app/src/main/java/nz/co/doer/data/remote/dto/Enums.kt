package nz.co.doer.data.remote.dto

import kotlinx.serialization.Serializable

// --- ShiftStatus ---
enum class ShiftStatus(val value: Int) {
    Created(1),
    Accepted(2),
    Ongoing(3),
    Completed(4),
    NotCompleted(5),
    FinishJob(6);

    companion object {
        fun fromValue(value: Int): ShiftStatus =
            entries.firstOrNull { it.value == value } ?: Created
    }
}

// --- UserRole ---
enum class UserRole(val roleName: String) {
    ADMINISTRATOR("ADMINISTRATOR"),
    MANAGER("MANAGER"),
    CONTRACTOR("CONTRACTOR"),
    CUSTOMER("CUSTOMER");

    companion object {
        fun fromName(name: String): UserRole =
            entries.firstOrNull { it.roleName.equals(name, ignoreCase = true) } ?: CONTRACTOR
    }
}

// --- Invoice ---
enum class Invoice(val value: Int) {
    NotYetCreated(1),
    ToBeInvoiced(2),
    InvoiceDrafted(3),
    InvoiceSent(4);

    companion object {
        fun fromValue(value: Int): Invoice =
            entries.firstOrNull { it.value == value } ?: NotYetCreated
    }
}

// --- ContractType ---
enum class ContractType(val value: Int) {
    ToBeConfirmed(1),
    FullContract(2),
    SupplyPlaceAndFinish(3),
    PlaceAndFinish(4),
    LabourSupply(5),
    BoxPlaceAndFinish(6),
    Remedial(7),
    SupplyPlaceFinishAndCut(8),
    PlaceFinishAndCut(9),
    OtherServices(10),
    Meetings(11);

    companion object {
        fun fromValue(value: Int): ContractType =
            entries.firstOrNull { it.value == value } ?: ToBeConfirmed
    }
}

// --- SubItemStatus ---
enum class SubItemStatus(val value: Int) {
    AwaitingPrevious(1),
    WorkingOnIt(2),
    Stuck(3),
    Done(4);

    companion object {
        fun fromValue(value: Int): SubItemStatus =
            entries.firstOrNull { it.value == value } ?: AwaitingPrevious
    }
}

// --- JobCategory ---
enum class JobCategory(val value: Int) {
    Primary(1),
    Secondary(2);

    fun toDisplayString(): String = when (this) {
        Primary -> "Primary"
        Secondary -> "Secondary"
    }

    companion object {
        fun fromValue(value: Int): JobCategory =
            entries.firstOrNull { it.value == value } ?: Primary
    }
}

// --- HSRequiredStatus ---
enum class HSRequiredStatus(val value: Int) {
    NoHS(0),
    SSSP(1),
    JSA(2),
    Take5(3),
    Done(4),
    MissingHS(5);

    companion object {
        fun fromValue(value: Int): HSRequiredStatus =
            entries.firstOrNull { it.value == value } ?: NoHS
    }
}

// --- LeadStatus ---
enum class LeadStatus(val value: Int) {
    NewLead(1),
    QuoteSent(2),
    Won(3),
    Contacted(4),
    QuoteExpired(5),
    Drafted(6);

    companion object {
        fun fromValue(value: Int): LeadStatus =
            entries.firstOrNull { it.value == value } ?: NewLead
    }
}

// --- NotificationStatus ---
enum class NotificationStatus(val value: Int) {
    Pending(0),
    Sent(1),
    Delivered(2),
    Failed(3),
    Read(4);

    companion object {
        fun fromValue(value: Int): NotificationStatus =
            entries.firstOrNull { it.value == value } ?: Sent
    }
}

// --- FrontEndPageVisibility ---
enum class FrontEndPageVisibility(val value: Int) {
    ShowAgreement(1),
    ShowInformation(2),
    ShowWelcomeScreen(3);
}

// --- ControlType ---
enum class ControlType {
    TextField,
    DropdownList,
    RadioButton,
    DatePicker,
    CheckBox,
    NumericTextField,
    TextLabel,
    TextKeyValueRadio
}

// --- MultipleUploadDocumentType ---
enum class MultipleUploadDocumentType {
    Image,
    Document,
    ExternalLink,
    Video
}

// --- FilterColumnType ---
enum class FilterColumnType {
    ItemColumn,
    SubItemColumn
}

// --- DoerTrackingState ---
enum class DoerTrackingState(val value: Int) {
    IDLE(0),
    CLOCKED_IN(1),
    EN_ROUTE(2),
    ARRIVED(3),
    ON_SITE(4),
    LEAVING(5),
    CLOCKED_OUT(6);

    companion object {
        fun fromValue(value: Int): DoerTrackingState =
            entries.firstOrNull { it.value == value } ?: IDLE

        /** Returns the set of states that are valid next transitions from [current]. */
        fun validTransitions(current: DoerTrackingState): Set<DoerTrackingState> = when (current) {
            IDLE -> setOf(CLOCKED_IN)
            CLOCKED_IN -> setOf(EN_ROUTE, ON_SITE, CLOCKED_OUT) // EN_ROUTE if heading to site, ON_SITE if already at yard/office
            EN_ROUTE -> setOf(ARRIVED, CLOCKED_OUT) // ARRIVED via geofence, CLOCKED_OUT for manual override
            ARRIVED -> setOf(ON_SITE, LEAVING, CLOCKED_OUT) // LEAVING if user exits before DWELL confirms
            ON_SITE -> setOf(LEAVING, CLOCKED_OUT)
            LEAVING -> setOf(ON_SITE, CLOCKED_OUT) // ON_SITE if re-enters within grace period
            CLOCKED_OUT -> setOf(CLOCKED_IN) // Can clock in again at a new site
        }
    }
}

// --- ClockLocationType ---
enum class ClockLocationType(val value: Int) {
    SITE(1),
    YARD(2),
    OFFICE(3);

    companion object {
        fun fromValue(value: Int): ClockLocationType =
            entries.firstOrNull { it.value == value } ?: SITE
    }
}

// --- ClockEventType ---
enum class ClockEventType(val value: String) {
    CLOCK_IN("CLOCK_IN"),
    CLOCK_OUT("CLOCK_OUT"),
    LOCATION_UPDATE("LOCATION_UPDATE"),
    GEOFENCE_ENTER("GEOFENCE_ENTER"),
    GEOFENCE_EXIT("GEOFENCE_EXIT"),
    STATE_CHANGE("STATE_CHANGE");

    companion object {
        fun fromValue(value: String): ClockEventType =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: STATE_CHANGE
    }
}

// --- FilterCondition ---
@Serializable
enum class FilterCondition {
    Contains,
    DoesNotContain,
    StartsWith,
    Is,
    IsNot,
    IsEmpty,
    IsNotEmpty,
    Equals,
    NotEquals,
    GreaterThan,
    LessThan,
    GreaterThanOrEqual,
    LessThanOrEqual;

    fun toDisplayString(): String = when (this) {
        Contains -> "Contains"
        DoesNotContain -> "Does Not Contain"
        StartsWith -> "Starts With"
        Is -> "Is"
        IsNot -> "Is Not"
        IsEmpty -> "Is Empty"
        IsNotEmpty -> "Is Not Empty"
        Equals -> "Equals"
        NotEquals -> "Not Equals"
        GreaterThan -> "Greater Than"
        LessThan -> "Less Than"
        GreaterThanOrEqual -> "Greater Than or Equal"
        LessThanOrEqual -> "Less Than or Equal"
    }
}
