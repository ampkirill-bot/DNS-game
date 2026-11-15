package ru.dks.obhod.data

enum class Plant(val title: String) {
    VALANZHIN("Валанжин"),
    SENOMAN("Сеноман"),
}

data class Gpa(val id: String, val plant: Plant, val number: Int) {
    val title: String get() = "ГПА-$number"
    val fullTitle: String get() = "${plant.title} · ГПА-$number"
}

object GpaList {
    val all: List<Gpa> =
        (1..12).map { Gpa("V$it", Plant.VALANZHIN, it) } +
            (1..3).map { Gpa("S$it", Plant.SENOMAN, it) }

    fun byId(id: String): Gpa = all.first { it.id == id }
    fun byPlant(plant: Plant): List<Gpa> = all.filter { it.plant == plant }
}
