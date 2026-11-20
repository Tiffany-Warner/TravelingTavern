package com.tiffles.travelingtavern.domain.model

/**
 * A SillyTavern persona: who the user is in a chat. [avatarId] is its ID (the picture's file
 * name). [description] may be cut short in lists.
 */
data class Persona(
    val avatarId: String,
    val name: String,
    val title: String,
    val description: String,
    val pictureUrl: String?,
    val isActive: Boolean,
    val isDefault: Boolean
)

/**
 * Persona fields to write. Null fields are left as SillyTavern has them. [pictureUri] is a
 * picture picked on the phone (a content URI).
 */
data class PersonaChanges(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val pictureUri: String? = null
)
