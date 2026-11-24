package com.example.data

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AllianceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

    fun observeAlliances(): Flow<List<Alliance>> =
        db.collection("alliances")
            .whereEqualTo("realmId", REALM_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Alliance::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.totalPower }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser != null) handleFirestoreError(root, OperationType.LIST, "alliances")
                throw error
            }

    fun observeMessages(allianceId: String, userId: String): Flow<List<AllianceMessage>> =
        db.collection("alliances").document(allianceId).collection("messages")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AllianceMessage::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.LIST, "alliances/$allianceId/messages")
                }
                throw error
            }

    fun observeRallies(allianceId: String, userId: String): Flow<List<AllianceRally>> =
        db.collection("alliances").document(allianceId).collection("rallies")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AllianceRally::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.LIST, "alliances/$allianceId/rallies")
                }
                throw error
            }

    fun observeAidRequests(allianceId: String, userId: String): Flow<List<ResourceAidRequest>> =
        db.collection("alliances").document(allianceId).collection("aidRequests")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ResourceAidRequest::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.LIST, "alliances/$allianceId/aidRequests")
                }
                throw error
            }

    fun observeMyInvites(userId: String): Flow<List<AllianceInvite>> =
        db.collection("allianceInvites")
            .whereEqualTo("targetUid", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AllianceInvite::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.status == "PENDING" }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.LIST, "allianceInvites")
                }
                throw error
            }

    suspend fun createAlliance(
        empire: PlayerEmpire,
        name: String,
        tag: String,
        motto: String,
        crestColor: String,
        openJoin: Boolean
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val cleanName = name.trim()
        val cleanTag = tag.trim().uppercase()
        val cleanMotto = motto.trim().take(120)
        if (cleanName.length !in 3..32) {
            return Result.failure(IllegalArgumentException("Alliance name must be 3–32 characters."))
        }
        if (cleanTag.length !in 2..5) {
            return Result.failure(IllegalArgumentException("Alliance tag must be 2–5 characters."))
        }
        if (empire.allianceId.isNotBlank()) {
            return Result.failure(IllegalStateException("Leave your current alliance first."))
        }
        val docRef = db.collection("alliances").document()
        return try {
            val alliance = Alliance(
                realmId = REALM_ID,
                name = cleanName,
                tag = cleanTag,
                motto = cleanMotto.ifBlank { "Forged in Obsidian and Gold" },
                crestColor = crestColor,
                leaderUid = uid,
                officerUids = emptyList(),
                memberUids = listOf(uid),
                memberCount = 1L,
                totalPower = empire.power,
                openJoin = openJoin
            )
            val batch = db.batch()
            batch.set(docRef, alliance.toCreateMap())
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "allianceId" to docRef.id,
                    "allianceTag" to cleanTag,
                    "allianceRole" to "LEADER",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("kingdoms").document(uid),
                mapOf(
                    "allianceId" to docRef.id,
                    "allianceTag" to cleanTag,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            // Post founding announcement in alliance chat
            val welcome = AllianceMessage(
                allianceId = docRef.id,
                senderUid = uid,
                senderName = empire.lordName,
                senderRole = "LEADER",
                body = "${empire.lordName} founded [$cleanTag] $cleanName!",
                messageType = "SYSTEM",
                memberUids = listOf(uid)
            )
            docRef.collection("messages").document().set(welcome.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinAlliance(
        empire: PlayerEmpire,
        alliance: Alliance,
        invite: AllianceInvite? = null
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (empire.allianceId.isNotBlank()) {
            return Result.failure(IllegalStateException("You are already in an alliance."))
        }
        if (alliance.memberUids.size >= 30) {
            return Result.failure(IllegalStateException("This alliance is already at 30/30 members."))
        }
        if (!alliance.openJoin && invite == null) {
            return Result.failure(IllegalStateException("This alliance is invite-only."))
        }
        val path = "alliances/${alliance.id}"
        return try {
            val updatedMembers = (alliance.memberUids + uid).distinct()
            val batch = db.batch()
            batch.update(
                db.collection("alliances").document(alliance.id),
                mapOf(
                    "memberUids" to updatedMembers,
                    "memberCount" to updatedMembers.size.toLong(),
                    "totalPower" to (alliance.totalPower + empire.power),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "allianceId" to alliance.id,
                    "allianceTag" to alliance.tag,
                    "allianceRole" to "MEMBER",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("kingdoms").document(uid),
                mapOf(
                    "allianceId" to alliance.id,
                    "allianceTag" to alliance.tag,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (invite != null) {
                batch.update(
                    db.collection("allianceInvites").document(invite.id),
                    mapOf("status" to "ACCEPTED", "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()

            val msg = AllianceMessage(
                allianceId = alliance.id,
                senderUid = uid,
                senderName = empire.lordName,
                senderRole = "MEMBER",
                body = "${empire.lordName} swore fealty to [${alliance.tag}]!",
                messageType = "SYSTEM",
                memberUids = updatedMembers
            )
            db.collection("alliances").document(alliance.id)
                .collection("messages").document().set(msg.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun declineInvite(invite: AllianceInvite): Result<Unit> {
        try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "allianceInvites/${invite.id}"
        return try {
            db.collection("allianceInvites").document(invite.id).update(
                mapOf("status" to "DECLINED", "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveOrDisbandAlliance(empire: PlayerEmpire, alliance: Alliance): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "alliances/${alliance.id}"
        return try {
            val batch = db.batch()
            if (alliance.leaderUid == uid) {
                if (alliance.memberUids.size > 1) {
                    return Result.failure(IllegalStateException("Transfer leadership before leaving."))
                }
                batch.delete(db.collection("alliances").document(alliance.id))
            } else {
                val updatedMembers = alliance.memberUids.filter { it != uid }
                val updatedOfficers = alliance.officerUids.filter { it != uid }
                batch.update(
                    db.collection("alliances").document(alliance.id),
                    mapOf(
                        "memberUids" to updatedMembers,
                        "officerUids" to updatedOfficers,
                        "memberCount" to updatedMembers.size.toLong(),
                        "totalPower" to (alliance.totalPower - empire.power).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "allianceId" to "",
                    "allianceTag" to "",
                    "allianceRole" to "NONE",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("kingdoms").document(uid),
                mapOf(
                    "allianceId" to "",
                    "allianceTag" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        empire: PlayerEmpire,
        alliance: Alliance,
        body: String,
        messageType: String = "CHAT"
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val clean = body.trim()
        if (clean.isEmpty() || clean.length > 300) {
            return Result.failure(IllegalArgumentException("Message must be 1–300 characters."))
        }
        val docRef = db.collection("alliances").document(alliance.id).collection("messages").document()
        return try {
            val role = alliance.roleOf(uid).takeIf { it != "NONE" } ?: "MEMBER"
            val msg = AllianceMessage(
                allianceId = alliance.id,
                senderUid = uid,
                senderName = empire.lordName,
                senderRole = role,
                body = clean,
                messageType = messageType,
                memberUids = alliance.memberUids
            )
            docRef.set(msg.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun launchRally(
        empire: PlayerEmpire,
        alliance: Alliance,
        targetType: String,
        targetId: String,
        targetName: String,
        targetX: Long,
        targetY: Long,
        targetPower: Long,
        committedPower: Long
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val rallyRef = db.collection("alliances").document(alliance.id).collection("rallies").document()
        return try {
            val powerCommit = committedPower.coerceAtLeast(100L)
            val rally = AllianceRally(
                allianceId = alliance.id,
                leaderUid = uid,
                leaderName = empire.lordName,
                targetType = targetType,
                targetId = targetId,
                targetName = targetName.take(48),
                targetX = targetX.coerceIn(0L, 99L),
                targetY = targetY.coerceIn(0L, 99L),
                targetPower = targetPower.coerceAtLeast(100L),
                totalPower = powerCommit,
                participantUids = listOf(uid),
                participantSummary = "${empire.lordName} (${formatCompact(powerCommit)})",
                launchAtEpochMs = System.currentTimeMillis() + 60_000L,
                status = "GATHERING",
                memberUids = alliance.memberUids
            )
            rallyRef.set(rally.toCreateMap()).await()
            sendMessage(
                empire,
                alliance,
                "WAR RALLY called against $targetName ($targetX, $targetY)! Join before launch!",
                messageType = "RALLY"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, rallyRef.path)
            Result.failure(e)
        }
    }

    suspend fun joinRally(
        empire: PlayerEmpire,
        alliance: Alliance,
        rally: AllianceRally,
        committedPower: Long
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (uid in rally.participantUids) {
            return Result.failure(IllegalStateException("You already joined this rally."))
        }
        val path = "alliances/${alliance.id}/rallies/${rally.id}"
        return try {
            val powerCommit = committedPower.coerceAtLeast(100L)
            val nextParticipants = rally.participantUids + uid
            val nextSummary = "${rally.participantSummary}, ${empire.lordName} (${formatCompact(powerCommit)})".take(300)
            db.collection("alliances").document(alliance.id)
                .collection("rallies").document(rally.id)
                .update(
                    mapOf(
                        "participantUids" to nextParticipants,
                        "participantSummary" to nextSummary,
                        "totalPower" to (rally.totalPower + powerCommit),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun resolveRally(
        empire: PlayerEmpire,
        alliance: Alliance,
        rally: AllianceRally
    ): Result<BattleLog> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "alliances/${alliance.id}/rallies/${rally.id}"
        return try {
            val npc = GameCatalog.worldNpcNodes.firstOrNull { it.id == rally.targetId }
            val outcome = GameCatalog.resolveCombat(
                attackerPower = rally.totalPower,
                siegeSent = empire.siege,
                defenderPower = rally.targetPower,
                targetType = rally.targetType,
                alliance = alliance,
                npcNode = npc
            )
            val battleLog = BattleLog(
                realmId = REALM_ID,
                attackerUid = uid,
                attackerName = "[${alliance.tag}] War Rally",
                attackerTag = alliance.tag,
                defenderUid = rally.targetId,
                defenderName = rally.targetName,
                defenderTag = if (rally.targetType == "BASTION") "NPC" else "PVP",
                battleType = "RALLY",
                victory = outcome.victory,
                attackerPower = outcome.effectiveAttackerPower,
                defenderPower = outcome.defenderPower,
                troopsLost = outcome.troopsLost,
                lootFood = outcome.lootFood,
                lootTimber = outcome.lootTimber,
                lootStone = outcome.lootStone,
                lootGold = outcome.lootGold,
                allianceId = alliance.id
            )
            val nextHonor = (empire.honor + outcome.honorGained).coerceAtMost(999_999_999L)
            val nextPower = empire.copy(honor = nextHonor).computeTotalPower()
            val batch = db.batch()
            batch.update(
                db.collection("alliances").document(alliance.id)
                    .collection("rallies").document(rally.id),
                mapOf("status" to "RESOLVED", "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.set(db.collection("battleLogs").document(), battleLog.toCreateMap())
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "food" to (empire.food + outcome.lootFood),
                    "timber" to (empire.timber + outcome.lootTimber),
                    "stone" to (empire.stone + outcome.lootStone),
                    "gold" to (empire.gold + outcome.lootGold),
                    "honor" to nextHonor,
                    "speedups" to if (outcome.victory) empire.speedups + 1L else empire.speedups,
                    "power" to nextPower,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("kingdoms").document(uid),
                mapOf("power" to nextPower, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.commit().await()
            val verdict = if (outcome.victory) "VICTORY" else "DEFEAT"
            sendMessage(
                empire,
                alliance,
                "Rally vs ${rally.targetName} ended in $verdict! Looted ${formatCompact(outcome.lootGold)} Gold.",
                messageType = "RALLY"
            )
            Result.success(battleLog)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun requestAid(
        empire: PlayerEmpire,
        alliance: Alliance,
        resourceType: ResourceType,
        amount: Long
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val clamped = amount.coerceIn(100L, 50_000L)
        val docRef = db.collection("alliances").document(alliance.id).collection("aidRequests").document()
        return try {
            val req = ResourceAidRequest(
                allianceId = alliance.id,
                requesterUid = uid,
                requesterName = empire.lordName,
                resourceType = resourceType.name,
                requestedAmount = clamped,
                fulfilledAmount = 0L,
                status = "OPEN",
                donorUids = emptyList(),
                memberUids = alliance.memberUids
            )
            docRef.set(req.toCreateMap()).await()
            sendMessage(
                empire,
                alliance,
                "Requested ${formatCompact(clamped)} ${resourceType.displayName} in Alliance Aid.",
                messageType = "AID"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun donateAid(
        empire: PlayerEmpire,
        alliance: Alliance,
        request: ResourceAidRequest
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (request.requesterUid == uid) {
            return Result.failure(IllegalStateException("You cannot fulfill your own aid request."))
        }
        val resType = ResourceType.fromRaw(request.resourceType)
        val remaining = (request.requestedAmount - request.fulfilledAmount).coerceAtLeast(0L)
        val donation = remaining.coerceAtMost(300L)
        if (donation <= 0L) return Result.failure(IllegalStateException("Request already fulfilled."))

        val available = when (resType) {
            ResourceType.FOOD -> empire.food
            ResourceType.TIMBER -> empire.timber
            ResourceType.STONE -> empire.stone
            ResourceType.GOLD -> empire.gold
        }
        if (available < donation) {
            return Result.failure(IllegalStateException("Need $donation ${resType.displayName} to donate."))
        }
        val path = "alliances/${alliance.id}/aidRequests/${request.id}"
        return try {
            val newFulfilled = request.fulfilledAmount + donation
            val newStatus = if (newFulfilled >= request.requestedAmount) "FULFILLED" else "OPEN"
            val resField = resType.name.lowercase()
            val batch = db.batch()
            batch.update(
                db.collection("alliances").document(alliance.id)
                    .collection("aidRequests").document(request.id),
                mapOf(
                    "fulfilledAmount" to newFulfilled,
                    "status" to newStatus,
                    "donorUids" to (request.donorUids + uid).distinct(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("alliances").document(alliance.id),
                mapOf(
                    "techXp" to (alliance.techXp + 75L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    resField to (available - donation),
                    "honor" to (empire.honor + 45L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun contributeAllianceTech(
        empire: PlayerEmpire,
        alliance: Alliance,
        techType: AllianceTechType
    ): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val currentLevel = alliance.techLevel(techType)
        if (currentLevel >= 10L) {
            return Result.failure(IllegalStateException("${techType.displayName} is at max Level 10."))
        }
        if (empire.gold < 120L) {
            return Result.failure(IllegalStateException("Donating to Alliance Tech costs 120 Gold."))
        }
        val path = "alliances/${alliance.id}"
        return try {
            val nextXp = alliance.techXp + 120L
            val neededXp = GameCatalog.techUpgradeCostXp(currentLevel)
            val leveledUp = nextXp >= neededXp
            val finalLevel = if (leveledUp) currentLevel + 1L else currentLevel
            val finalXp = if (leveledUp) nextXp - neededXp else nextXp

            val batch = db.batch()
            batch.update(
                db.collection("alliances").document(alliance.id),
                mapOf(
                    techType.firestoreField to finalLevel,
                    "techXp" to finalXp,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "gold" to (empire.gold - 120L),
                    "honor" to (empire.honor + 60L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            val note = if (leveledUp) {
                "${techType.displayName} upgraded to Level $finalLevel for all members!"
            } else {
                "+120 Tech XP contributed to ${techType.displayName} (+60 Honor)"
            }
            Result.success(note)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun inviteLord(
        empire: PlayerEmpire,
        alliance: Alliance,
        target: RealmKingdom
    ): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val inviteId = "${alliance.id}_${target.ownerUid}"
        return try {
            val invite = AllianceInvite(
                allianceId = alliance.id,
                allianceName = alliance.name,
                allianceTag = alliance.tag,
                inviterUid = uid,
                inviterName = empire.lordName,
                targetUid = target.ownerUid,
                targetName = target.lordName,
                status = "PENDING"
            )
            db.collection("allianceInvites").document(inviteId).set(invite.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "allianceInvites/$inviteId")
            Result.failure(e)
        }
    }

    suspend fun toggleOfficer(alliance: Alliance, targetUid: String): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (alliance.leaderUid != uid) {
            return Result.failure(IllegalStateException("Only the R5 Leader can promote or demote officers."))
        }
        val path = "alliances/${alliance.id}"
        return try {
            val isOfficer = targetUid in alliance.officerUids
            val nextOfficers = if (isOfficer) {
                alliance.officerUids.filter { it != targetUid }
            } else {
                (alliance.officerUids + targetUid).distinct().take(10)
            }
            db.collection("alliances").document(alliance.id).update(
                mapOf("officerUids" to nextOfficers, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            Result.success(if (isOfficer) "Demoted to R1 Member." else "Promoted to R4 Officer!")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun transferLeadership(alliance: Alliance, newLeaderUid: String): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (alliance.leaderUid != uid || newLeaderUid !in alliance.memberUids) {
            return Result.failure(IllegalStateException("Invalid leadership transfer."))
        }
        val path = "alliances/${alliance.id}"
        return try {
            val nextOfficers = (alliance.officerUids.filter { it != newLeaderUid } + uid).distinct().take(10)
            val batch = db.batch()
            batch.update(
                db.collection("alliances").document(alliance.id),
                mapOf(
                    "leaderUid" to newLeaderUid,
                    "officerUids" to nextOfficers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "allianceId" to alliance.id,
                    "allianceTag" to alliance.tag,
                    "allianceRole" to "OFFICER",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun kickMember(alliance: Alliance, targetUid: String, targetPower: Long): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val canManage = alliance.leaderUid == uid || uid in alliance.officerUids
        if (!canManage || targetUid == alliance.leaderUid) {
            return Result.failure(IllegalStateException("Insufficient rank to remove this member."))
        }
        val path = "alliances/${alliance.id}"
        return try {
            val nextMembers = alliance.memberUids.filter { it != targetUid }
            val nextOfficers = alliance.officerUids.filter { it != targetUid }
            db.collection("alliances").document(alliance.id).update(
                mapOf(
                    "memberUids" to nextMembers,
                    "officerUids" to nextOfficers,
                    "memberCount" to nextMembers.size.toLong(),
                    "totalPower" to (alliance.totalPower - targetPower).coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
