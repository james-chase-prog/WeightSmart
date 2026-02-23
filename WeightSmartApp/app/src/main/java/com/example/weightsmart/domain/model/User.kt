package com.example.weightsmart.domain.model

import java.time.Instant

/**
 * Domain model for a User (not a Room entity).
 *
 * Required on creation: username, password, email, goalWeight.
 * Optional: nickname, goalDate, currentWeight (system-managed), phoneNumber, smsConsent, avatar.
 *
 * NOTE: Keep `password` only during creation/change flows. Persist a HASH in the data layer.
 * NOTE: Units — this app uses **pounds (lb)** for goal/current weight.
 */
class User(
    username: String,
    password: CharArray,          // required; DO NOT persist plaintext
    email: String,
    goalWeight: Double,           // required (value in **pounds**, one-decimal handling at repo/UI boundaries)
    nickname: String? = null,
    goalDate: Instant? = null,
    currentWeight: Double? = null, // not set at creation; updated from latest WeightEntry
    phoneNumber: String? = null,
    smsConsent: Boolean? = null,   // null = not asked yet
    avatar: String = DEFAULT_AVATAR
) {
    companion object {
        const val DEFAULT_AVATAR: String = "avatar_default"
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
        private val PHONE_REGEX = Regex("^\\+?[0-9 .\\-()]{7,20}$")
        private const val MIN_USERNAME = 3
        private const val MAX_USERNAME = 30
        private const val MIN_PASSWORD_LEN = 8
        private const val MAX_NICKNAME = 20
        private const val MIN_WEIGHT = 31.0   // sanity bounds (**lb** ~14 kg) — adjust as needed
        private const val MAX_WEIGHT = 1400.0 // sanity bounds (**lb**)
    }

    // Backing fields
    private var _username: String = username.trim()
    private var _password: CharArray = password
    private var _email: String = email.trim()
    private var _nickname: String? = nickname?.trim()?.ifBlank { null }
    private var _goalWeight: Double = goalWeight
    private var _goalDate: Instant? = goalDate
    private var _currentWeight: Double? = currentWeight
    private var _phoneNumber: String? = phoneNumber?.trim()?.ifBlank { null }
    private var _smsConsent: Boolean? = smsConsent
    private var _avatar: String = avatar

    init {
        validateUsername(_username)
        validatePassword(_password)
        validateEmail(_email)
        validateNickname(_nickname)
        validateWeightRequired(_goalWeight)
        validateWeightOptional(_currentWeight)
        validateGoalDate(_goalDate)
        validatePhone(_phoneNumber)
    }

    // ---- Getters ----
    fun getUsername(): String = _username
    fun getEmail(): String = _email
    fun getNickname(): String? = _nickname
    fun getGoalWeight(): Double = _goalWeight
    fun getGoalDate(): Instant? = _goalDate
    fun getCurrentWeight(): Double? = _currentWeight
    fun getPhoneNumber(): String? = _phoneNumber
    fun getSmsConsent(): Boolean? = _smsConsent
    fun getAvatar(): String = _avatar

    /** Access password during creation/change flows only. */
    fun getPassword(): CharArray = _password

    // ---- Setters with validation ----
    fun setUsername(value: String) {
        val v = value.trim()
        validateUsername(v)
        _username = v
    }

    fun setPassword(newPassword: CharArray) {
        validatePassword(newPassword)
        _password.fill('\u0000')
        _password = newPassword
    }

    fun clearPassword() {
        _password.fill('\u0000')
        _password = CharArray(0)
    }

    fun setEmail(value: String) {
        val v = value.trim()
        validateEmail(v)
        _email = v
    }

    fun setNickname(value: String?) {
        val v = value?.trim()?.ifBlank { null }
        validateNickname(v)
        _nickname = v
    }

    fun setGoalWeight(value: Double) {
        validateWeightRequired(value)
        _goalWeight = value
    }

    fun setGoalDate(value: Instant?) {
        validateGoalDate(value)
        _goalDate = value
    }

    /** System-managed: call when latest WeightEntry changes. */
    fun setCurrentWeight(value: Double?) {
        validateWeightOptional(value)
        _currentWeight = value
    }

    fun setPhoneNumber(value: String?) {
        val v = value?.trim()?.ifBlank { null }
        validatePhone(v)
        _phoneNumber = v
    }

    fun setSmsConsent(value: Boolean?) { _smsConsent = value }

    fun setAvatar(value: String) { _avatar = value }

    // ---- Validation helpers ----
    private fun validateUsername(v: String) {
        require(v.length in MIN_USERNAME..MAX_USERNAME) { "Username must be $MIN_USERNAME..$MAX_USERNAME characters." }
        require(!v.contains(' ')) { "Username must not contain spaces." }
    }

    private fun validatePassword(pw: CharArray) {
        require(pw.size >= MIN_PASSWORD_LEN) { "Password must be at least $MIN_PASSWORD_LEN characters." }
    }

    private fun validateEmail(v: String) {
        require(EMAIL_REGEX.matches(v)) { "Invalid email address." }
    }

    private fun validateNickname(v: String?) {
        if (v != null) require(v.length <= MAX_NICKNAME) { "Nickname too long (>$MAX_NICKNAME)." }
    }

    private fun validateWeightRequired(w: Double) {
        require(w in MIN_WEIGHT..MAX_WEIGHT) { "Goal weight out of range (lb)." }
    }

    private fun validateWeightOptional(w: Double?) {
        if (w != null) require(w in MIN_WEIGHT..MAX_WEIGHT) { "Current weight out of range (lb)." }
    }

    private fun validateGoalDate(d: Instant?) {
        // allowed to be null; if non-null, must be a valid Instant (already ensured by type)
        // You can add bounds checks here if desired.
    }

    private fun validatePhone(v: String?) {
        if (v != null) require(PHONE_REGEX.matches(v)) { "Invalid phone number." }
    }

    // ---- Convenience ----
    fun hasPendingSmsSetup(): Boolean = _phoneNumber == null && _smsConsent == null
}
