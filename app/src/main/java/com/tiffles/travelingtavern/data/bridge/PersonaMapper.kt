package com.tiffles.travelingtavern.data.bridge

import com.tiffles.travelingtavern.domain.model.Persona

internal fun PersonaDto.toDomain() = Persona(
    avatarId = avatarId,
    name = name,
    title = title,
    description = description,
    pictureUrl = pictureUrl,
    isActive = isActive,
    isDefault = isDefault
)
