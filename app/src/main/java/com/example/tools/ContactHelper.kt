package com.example.tools

import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract
import android.util.Log

data class ResolvedContact(
    val displayName: String,
    val rawNumber: String,
    val formattedWhatsAppNumber: String,
    val last3Digits: String
)

sealed class ContactResolutionResult {
    data class SingleMatch(val contact: ResolvedContact, val isFromMemory: Boolean) : ContactResolutionResult()
    data class MultipleMatches(val name: String, val matches: List<ResolvedContact>) : ContactResolutionResult()
    data class NoMatch(val searchName: String) : ContactResolutionResult()
    data class DirectNumber(val formattedNumber: String) : ContactResolutionResult()
}

object ContactHelper {

    private const val PREFS_NAME = "ZoyaContactMemory"
    private const val DEFAULT_COUNTRY_CODE = "91" // India default (+91)

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save the user's preferred number for a contact name.
     */
    fun saveContactPreference(context: Context, contactName: String, formattedNumber: String) {
        val cleanKey = sanitizeForLookup(contactName)
        if (cleanKey.isNotEmpty() && formattedNumber.isNotEmpty()) {
            getPrefs(context).edit().putString("pref_$cleanKey", formattedNumber).apply()
            Log.d("ContactHelper", "Saved preference: $cleanKey -> $formattedNumber")
        }
    }

    /**
     * Get the saved preference for a contact name if any.
     */
    fun getSavedPreference(context: Context, contactName: String): String? {
        val cleanKey = sanitizeForLookup(contactName)
        return getPrefs(context).getString("pref_$cleanKey", null)
    }

    /**
     * Cleans and formats any phone number for WhatsApp international URL standard (digits only with country code).
     * Examples:
     * "9876543210" -> "919876543210"
     * "+91 98765 43210" -> "919876543210"
     * "09876543210" -> "919876543210"
     * "+1 (555) 234-5678" -> "15552345678"
     */
    fun formatForWhatsApp(rawNumber: String, defaultCountryCode: String = DEFAULT_COUNTRY_CODE): String {
        // Strip everything except digits
        var digits = rawNumber.replace(Regex("[^0-9]"), "")

        if (digits.isEmpty()) return ""

        // Handle Indian 10-digit mobile numbers (usually starts with 6,7,8,9)
        if (digits.length == 10) {
            return "$defaultCountryCode$digits"
        }

        // Handle 11-digit numbers starting with 0 (e.g. 09876543210)
        if (digits.length == 11 && digits.startsWith("0")) {
            return "$defaultCountryCode${digits.substring(1)}"
        }

        // Handle 12-digit numbers starting with 91 (already formatted)
        if (digits.length == 12 && digits.startsWith("91")) {
            return digits
        }

        // Handle international numbers (starts with country code)
        return digits
    }

    private fun sanitizeForLookup(text: String): String {
        return text.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
    }

    /**
     * Resolves a contact query. Checks:
     * 1. Direct phone number inputs
     * 2. Number suffix hints (e.g. "Rahul 456" or "Rahul last 456")
     * 3. Saved preferences in memory
     * 4. Full phone ContactsContract search with exact, prefix, contains, and fuzzy matching
     */
    fun resolveContact(context: Context, query: String): ContactResolutionResult {
        val trimmedQuery = query.trim()

        // Check if query is directly a phone number (7+ digits or starting with +)
        val digitCount = trimmedQuery.count { it.isDigit() }
        if (digitCount >= 7 && (trimmedQuery.startsWith("+") || trimmedQuery.matches(Regex("^[0-9+\\- ()]+$")))) {
            val formatted = formatForWhatsApp(trimmedQuery)
            return ContactResolutionResult.DirectNumber(formatted)
        }

        // Extract any 3+ digit suffix hint from the query (e.g. "Rahul 456" -> name: "Rahul", suffix: "456")
        val digitMatch = Regex("(\\d{3,10})").find(trimmedQuery)
        val digitHint = digitMatch?.value
        val nameOnly = if (digitHint != null) {
            trimmedQuery.replace(digitHint, "").replace("last", "").replace("ending", "").trim()
        } else {
            trimmedQuery
        }

        // Query device contacts
        val allMatchingContacts = searchDeviceContacts(context, if (nameOnly.isNotEmpty()) nameOnly else trimmedQuery)

        if (allMatchingContacts.isEmpty()) {
            return ContactResolutionResult.NoMatch(trimmedQuery)
        }

        // If user provided a digit hint (e.g. last 3 digits "456"), filter contacts matching that digit suffix
        if (digitHint != null && digitHint.isNotEmpty()) {
            val suffixMatched = allMatchingContacts.filter {
                val cleanNum = it.rawNumber.replace(Regex("[^0-9]"), "")
                cleanNum.endsWith(digitHint) || cleanNum.contains(digitHint)
            }
            if (suffixMatched.isNotEmpty()) {
                val chosen = suffixMatched.first()
                // Save this choice to memory!
                saveContactPreference(context, nameOnly.ifEmpty { trimmedQuery }, chosen.formattedWhatsAppNumber)
                return ContactResolutionResult.SingleMatch(chosen, isFromMemory = false)
            }
        }

        // Check persistent memory if there's only a name without explicit digits
        val savedNumber = getSavedPreference(context, nameOnly.ifEmpty { trimmedQuery })
        if (savedNumber != null) {
            val memoryMatch = allMatchingContacts.firstOrNull { it.formattedWhatsAppNumber == savedNumber }
            if (memoryMatch != null) {
                return ContactResolutionResult.SingleMatch(memoryMatch, isFromMemory = true)
            }
        }

        // If only 1 unique number found
        val uniqueNumbers = allMatchingContacts.distinctBy { it.formattedWhatsAppNumber }
        if (uniqueNumbers.size == 1) {
            val chosen = uniqueNumbers.first()
            saveContactPreference(context, nameOnly.ifEmpty { trimmedQuery }, chosen.formattedWhatsAppNumber)
            return ContactResolutionResult.SingleMatch(chosen, isFromMemory = false)
        }

        // Multiple distinct numbers exist for this name
        return ContactResolutionResult.MultipleMatches(nameOnly.ifEmpty { trimmedQuery }, uniqueNumbers)
    }

    private fun searchDeviceContacts(context: Context, namePattern: String): List<ResolvedContact> {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }

        val results = mutableListOf<ResolvedContact>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                val exactList = mutableListOf<ResolvedContact>()
                val prefixList = mutableListOf<ResolvedContact>()
                val containsList = mutableListOf<ResolvedContact>()
                val fuzzyList = mutableListOf<Pair<Int, ResolvedContact>>()

                val cleanPattern = namePattern.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
                val patternNoSpace = cleanPattern.replace(" ", "")
                val searchWords = cleanPattern.split(" ").filter { it.isNotEmpty() }

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIdx) ?: continue
                    val rawNumber = cursor.getString(numIdx) ?: continue

                    val cleanContactName = displayName.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
                    if (cleanContactName.isEmpty()) continue

                    val formatted = formatForWhatsApp(rawNumber)
                    val digitsOnly = rawNumber.replace(Regex("[^0-9]"), "")
                    val last3 = if (digitsOnly.length >= 3) digitsOnly.takeLast(3) else digitsOnly

                    val contact = ResolvedContact(
                        displayName = displayName,
                        rawNumber = rawNumber,
                        formattedWhatsAppNumber = formatted,
                        last3Digits = last3
                    )

                    val contactNoSpace = cleanContactName.replace(" ", "")

                    if (contactNoSpace == patternNoSpace || cleanContactName == cleanPattern) {
                        exactList.add(contact)
                    } else if (contactNoSpace.startsWith(patternNoSpace) || cleanContactName.startsWith(cleanPattern)) {
                        prefixList.add(contact)
                    } else if (searchWords.isNotEmpty() && searchWords.all { cleanContactName.contains(it) }) {
                        containsList.add(contact)
                    } else if (patternNoSpace.length > 2 && contactNoSpace.contains(patternNoSpace)) {
                        containsList.add(contact)
                    }

                    val distance = levenshtein(contactNoSpace, patternNoSpace)
                    if (distance <= 2 && patternNoSpace.length > 3) {
                        fuzzyList.add(Pair(distance, contact))
                    }
                }

                if (exactList.isNotEmpty()) return exactList.distinctBy { it.formattedWhatsAppNumber }
                if (prefixList.isNotEmpty()) return prefixList.distinctBy { it.formattedWhatsAppNumber }
                if (containsList.isNotEmpty()) return containsList.distinctBy { it.formattedWhatsAppNumber }
                if (fuzzyList.isNotEmpty()) {
                    return fuzzyList.sortedBy { it.first }.map { it.second }.distinctBy { it.formattedWhatsAppNumber }
                }
            }
        } catch (e: Exception) {
            Log.e("ContactHelper", "Error searching contacts", e)
        }
        return emptyList()
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length
        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)

        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }
}
