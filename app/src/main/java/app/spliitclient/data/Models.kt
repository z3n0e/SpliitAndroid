package app.spliitclient.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Modelli che rispecchiano le risposte tRPC di Spliit (campi sconosciuti ignorati).

@Serializable
data class Participant(val id: String, val name: String)

@Serializable
data class Group(
    val id: String,
    val name: String,
    val information: String? = null,
    val currency: String = "$",
    val currencyCode: String? = null,
    val participants: List<Participant> = emptyList(),
)

@Serializable
data class CountInfo(val participants: Int = 0, val documents: Int = 0)

@Serializable
data class GroupSummary(
    val id: String,
    val name: String,
    val currency: String = "$",
    val currencyCode: String? = null,
    @SerialName("_count") val count: CountInfo? = null,
)

@Serializable
data class GroupDetails(
    val group: Group,
    val participantsWithExpenses: List<String> = emptyList(),
)

@Serializable
data class CategoryRef(val id: Int = 0, val grouping: String = "", val name: String = "")

@Serializable
data class NamedRef(val id: String, val name: String = "")

@Serializable
data class PaidForRow(val participant: NamedRef, val shares: Long = 0)

@Serializable
data class ExpenseItem(
    val id: String,
    val title: String,
    val amount: Long,
    val category: CategoryRef? = null,
    val expenseDate: String = "",
    val createdAt: String = "",
    val isReimbursement: Boolean = false,
    val paidBy: NamedRef,
    val paidFor: List<PaidForRow> = emptyList(),
    val splitMode: String = "EVENLY",
    val recurrenceRule: String? = null,
    @SerialName("_count") val count: CountInfo? = null,
)

@Serializable
data class ExpensePage(
    val expenses: List<ExpenseItem> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Int = 0,
)

@Serializable
data class PaidForDetail(val participantId: String, val shares: Long = 0)

@Serializable
data class ExpenseDocument(val id: String, val url: String, val width: Int = 1, val height: Int = 1)

@Serializable
data class ExpenseDetail(
    val id: String,
    val title: String,
    val amount: Long,
    val expenseDate: String = "",
    val categoryId: Int = 0,
    val paidById: String,
    val paidFor: List<PaidForDetail> = emptyList(),
    val isReimbursement: Boolean = false,
    val splitMode: String = "EVENLY",
    val notes: String? = null,
    val recurrenceRule: String? = null,
    val originalAmount: Long? = null,
    val originalCurrency: String? = null,
    val conversionRate: JsonElement? = null,
    val documents: List<ExpenseDocument> = emptyList(),
)

@Serializable
data class BalanceRow(val paid: Double = 0.0, val paidFor: Double = 0.0, val total: Double = 0.0)

@Serializable
data class Reimbursement(val from: String, val to: String, val amount: Double)

@Serializable
data class BalancesResult(
    val balances: Map<String, BalanceRow> = emptyMap(),
    val reimbursements: List<Reimbursement> = emptyList(),
)

@Serializable
data class ActivityExpense(val title: String? = null)

@Serializable
data class GroupActivity(
    val id: String,
    val time: String = "",
    val activityType: String = "",
    val participantId: String? = null,
    val expenseId: String? = null,
    val data: String? = null,
    val expense: ActivityExpense? = null,
)

@Serializable
data class ActivityPage(
    val activities: List<GroupActivity> = emptyList(),
    val hasMore: Boolean = false,
    val nextCursor: Int = 0,
)

@Serializable
data class CategorySpending(
    val categoryId: Int = 0,
    val grouping: String = "",
    val name: String = "",
    val total: Double = 0.0,
)

@Serializable
data class StatsOverview(
    val totalGroupSpendings: Double = 0.0,
    val totalParticipantSpendings: Double? = null,
    val totalParticipantShare: Double? = null,
    val categories: List<CategorySpending> = emptyList(),
)

@Serializable
data class UserBalance(
    val groupId: String,
    val participantId: String,
    val amount: Double = 0.0,
    val currency: String = "$",
    val currencyCode: String? = null,
)

@Serializable
data class GroupFormParticipant(val id: String? = null, val name: String)

@Serializable
data class GroupFormValues(
    val name: String,
    val information: String = "",
    val currency: String,
    val currencyCode: String = "",
    val participants: List<GroupFormParticipant>,
)

enum class SplitMode(val label: String) {
    EVENLY("In parti uguali"),
    BY_SHARES("Per quote"),
    BY_PERCENTAGE("Percentuale"),
    BY_AMOUNT("Importi");

    companion object {
        fun from(value: String?): SplitMode = entries.firstOrNull { it.name == value } ?: EVENLY
    }
}

enum class Recurrence(val label: String) {
    NONE("Mai"),
    DAILY("Ogni giorno"),
    WEEKLY("Ogni settimana"),
    MONTHLY("Ogni mese"),
    YEARLY("Ogni anno");

    companion object {
        fun from(value: String?): Recurrence = entries.firstOrNull { it.name == value } ?: NONE
    }
}
