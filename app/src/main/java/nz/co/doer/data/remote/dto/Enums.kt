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
