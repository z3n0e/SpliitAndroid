package app.spliitclient.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Chiamate tipizzate verso le procedure tRPC di Spliit. */
class SpliitApi(private val client: TrpcClient) {

    private val json = client.json

    private fun JsonElement.field(name: String): JsonElement? =
        (this as? JsonObject)?.get(name)?.takeIf { it !is JsonNull }

    private fun JsonElement.stringField(name: String): String =
        ((this as? JsonObject)?.get(name) as? JsonPrimitive)?.content
            ?: throw ApiException("Risposta incompleta dal server.", ApiException.Kind.Server)

    suspend fun getGroup(groupId: String): Group? {
        val result = client.query("groups.get", buildJsonObject { put("groupId", groupId) })
        val group = result.field("group") ?: return null
        return json.decodeFromJsonElement(Group.serializer(), group)
    }

    suspend fun getGroupDetails(groupId: String): GroupDetails =
        json.decodeFromJsonElement(
            GroupDetails.serializer(),
            client.query("groups.getDetails", buildJsonObject { put("groupId", groupId) }),
        )

    suspend fun listGroups(ids: List<String>): List<GroupSummary> {
        val out = mutableListOf<GroupSummary>()
        for (chunk in ids.distinct().chunked(50)) {
            val result = client.query("groups.list", buildJsonObject {
                putJsonArray("groupIds") { chunk.forEach { add(it) } }
            })
            val groups = result.field("groups") ?: continue
            out += json.decodeFromJsonElement(ListSerializer(GroupSummary.serializer()), groups)
        }
        return out
    }

    suspend fun createGroup(form: GroupFormValues): String {
        val result = client.mutation("groups.create", buildJsonObject {
            put("groupFormValues", json.encodeToJsonElement(GroupFormValues.serializer(), form))
        })
        return result.stringField("groupId")
    }

    suspend fun updateGroup(groupId: String, form: GroupFormValues, participantId: String?) {
        client.mutation("groups.update", buildJsonObject {
            put("groupId", groupId)
            put("groupFormValues", json.encodeToJsonElement(GroupFormValues.serializer(), form))
            if (participantId != null) put("participantId", participantId)
        })
    }

    suspend fun listExpenses(groupId: String, cursor: Int, limit: Int, filter: String?): ExpensePage =
        json.decodeFromJsonElement(
            ExpensePage.serializer(),
            client.query("groups.expenses.list", buildJsonObject {
                put("groupId", groupId)
                put("cursor", cursor)
                put("limit", limit)
                if (!filter.isNullOrBlank()) put("filter", filter.take(200))
            }),
        )

    suspend fun getExpense(groupId: String, expenseId: String): ExpenseDetail {
        val result = client.query("groups.expenses.get", buildJsonObject {
            put("groupId", groupId)
            put("expenseId", expenseId)
        })
        val expense = result.field("expense")
            ?: throw ApiException("Spesa non trovata.", ApiException.Kind.NotFound)
        return json.decodeFromJsonElement(ExpenseDetail.serializer(), expense)
    }

    suspend fun createExpense(groupId: String, values: JsonObject, participantId: String?): String {
        val result = client.mutation("groups.expenses.create", buildJsonObject {
            put("groupId", groupId)
            put("expenseFormValues", values)
            if (participantId != null) put("participantId", participantId)
        })
        return result.stringField("expenseId")
    }

    suspend fun updateExpense(groupId: String, expenseId: String, values: JsonObject, participantId: String?): String {
        val result = client.mutation("groups.expenses.update", buildJsonObject {
            put("expenseId", expenseId)
            put("groupId", groupId)
            put("expenseFormValues", values)
            if (participantId != null) put("participantId", participantId)
        })
        return result.stringField("expenseId")
    }

    suspend fun deleteExpense(groupId: String, expenseId: String, participantId: String?) {
        client.mutation("groups.expenses.delete", buildJsonObject {
            put("expenseId", expenseId)
            put("groupId", groupId)
            if (participantId != null) put("participantId", participantId)
        })
    }

    suspend fun balances(groupId: String): BalancesResult =
        json.decodeFromJsonElement(
            BalancesResult.serializer(),
            client.query("groups.balances.list", buildJsonObject { put("groupId", groupId) }),
        )

    suspend fun balancesForUser(pairs: List<Pair<String, String>>): List<UserBalance> {
        val out = mutableListOf<UserBalance>()
        for (chunk in pairs.chunked(40)) {
            val result = client.query("groups.balances.forUser", buildJsonObject {
                putJsonArray("groups") {
                    chunk.forEach { (groupId, participantId) ->
                        addJsonObject {
                            put("groupId", groupId)
                            put("participantId", participantId)
                        }
                    }
                }
            })
            val balances = result.field("balances") ?: continue
            out += json.decodeFromJsonElement(ListSerializer(UserBalance.serializer()), balances)
        }
        return out
    }

    suspend fun activities(groupId: String, cursor: Int, limit: Int): ActivityPage =
        json.decodeFromJsonElement(
            ActivityPage.serializer(),
            client.query("groups.activities.list", buildJsonObject {
                put("groupId", groupId)
                put("cursor", cursor)
                put("limit", limit)
            }),
        )

    suspend fun statsOverview(groupId: String, participantId: String?): StatsOverview =
        json.decodeFromJsonElement(
            StatsOverview.serializer(),
            client.query("groups.stats.overview", buildJsonObject {
                put("groupId", groupId)
                if (participantId != null) put("participantId", participantId)
            }),
        )
}
