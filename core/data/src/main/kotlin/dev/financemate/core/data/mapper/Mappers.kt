package dev.financemate.core.data.mapper

import dev.financemate.core.data.entity.AccountEntity
import dev.financemate.core.data.entity.CategoryEntity
import dev.financemate.core.data.entity.ImportBatchEntity
import dev.financemate.core.data.entity.TransactionEntity
import dev.financemate.core.model.Account
import dev.financemate.core.model.AccountId
import dev.financemate.core.model.AccountType
import dev.financemate.core.model.Category
import dev.financemate.core.model.CategoryId
import dev.financemate.core.model.CategoryKind
import dev.financemate.core.model.ImportBatch
import dev.financemate.core.model.ImportBatchId
import dev.financemate.core.model.ImportSource
import dev.financemate.core.model.MerchantKey
import dev.financemate.core.model.Transaction
import dev.financemate.core.model.TransactionId
import dev.financemate.core.money.CurrencyCode
import dev.financemate.core.money.Money
import java.time.Instant
import java.time.LocalDate

/**
 * Conversions between storage rows and domain objects.
 *
 * These are hand-written rather than generated so the storage representation can
 * change without the domain model following it. Two choices worth noting:
 *
 * - Dates are stored as **epoch day**, an integer, so range queries and sorting
 *   use the index directly rather than parsing strings.
 * - Enums are stored by `name`, not by ordinal. Ordinals are positional: adding
 *   a value in the middle of an enum would silently reinterpret every existing
 *   row. Names cost a few bytes and cannot do that.
 */

public fun AccountEntity.toDomain(): Account = Account(
    id = AccountId(id),
    displayName = displayName,
    institution = institution,
    type = AccountType.valueOf(type),
    currency = CurrencyCode(currency),
    mask = mask,
    isArchived = isArchived,
)

public fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id.value,
    displayName = displayName,
    institution = institution,
    type = type.name,
    currency = currency.code,
    mask = mask,
    isArchived = isArchived,
)

public fun CategoryEntity.toDomain(): Category = Category(
    id = CategoryId(id),
    name = name,
    kind = CategoryKind.valueOf(kind),
    parentId = parentId?.let { CategoryId(it) },
    isEssential = isEssential,
    isArchived = isArchived,
)

public fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id.value,
    name = name,
    kind = kind.name,
    parentId = parentId?.value,
    isEssential = isEssential,
    isArchived = isArchived,
)

public fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = TransactionId(id),
    accountId = AccountId(accountId),
    postedDate = LocalDate.ofEpochDay(postedDate),
    amount = Money(amountMinorUnits, CurrencyCode(currency)),
    rawDescription = rawDescription,
    merchantKey = MerchantKey(merchantKey),
    categoryId = categoryId?.let { CategoryId(it) },
    dedupHash = dedupHash,
    importBatchId = importBatchId?.let { ImportBatchId(it) },
    institutionTransactionId = institutionTransactionId,
    isPending = isPending,
    isTransfer = isTransfer,
    notes = notes,
    tags = tags?.split(TAG_SEPARATOR)?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
)

public fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id.value,
    accountId = accountId.value,
    postedDate = postedDate.toEpochDay(),
    amountMinorUnits = amount.minorUnits,
    currency = amount.currency.code,
    rawDescription = rawDescription,
    merchantKey = merchantKey.value,
    categoryId = categoryId?.value,
    dedupHash = dedupHash,
    importBatchId = importBatchId?.value,
    institutionTransactionId = institutionTransactionId,
    isPending = isPending,
    isTransfer = isTransfer,
    notes = notes,
    tags = tags.takeIf { it.isNotEmpty() }?.joinToString(TAG_SEPARATOR.toString()),
)

public fun ImportBatchEntity.toDomain(): ImportBatch = ImportBatch(
    id = ImportBatchId(id),
    accountId = AccountId(accountId),
    source = ImportSource.valueOf(source),
    importedAt = Instant.ofEpochMilli(importedAtEpochMillis),
    fileName = fileName,
    rowsParsed = rowsParsed,
    rowsImported = rowsImported,
    rowsDuplicate = rowsDuplicate,
    rowsFailed = rowsFailed,
)

public fun ImportBatch.toEntity(): ImportBatchEntity = ImportBatchEntity(
    id = id.value,
    accountId = accountId.value,
    source = source.name,
    importedAtEpochMillis = importedAt.toEpochMilli(),
    fileName = fileName,
    rowsParsed = rowsParsed,
    rowsImported = rowsImported,
    rowsDuplicate = rowsDuplicate,
    rowsFailed = rowsFailed,
)

/**
 * Tags are joined with a unit separator rather than a comma, so a tag containing
 * a comma cannot split itself into two on the way back out.
 */
private val TAG_SEPARATOR: Char = Char(31)
