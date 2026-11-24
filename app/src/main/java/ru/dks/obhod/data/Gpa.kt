package ru.dks.obhod.data

enum class Plant(val title: String) {
    VALANZHIN("Валанжин"),
    SENOMAN("Сеноман"),
}

data class Gpa(val id: String, val plant: Plant, val number: Int) {
    val title: String get() = "ГПА-$number"
    val fullTitle: String get() = "${plant.title} · ГПА-$number"
    /** Пожарные баллоны есть только на Валанжинских ГПА. */
    val hasCylinders: Boolean get() = plant == Plant.VALANZHIN
}

object GpaList {
    val all: List<Gpa> =
        (1..12).map { Gpa("V$it", Plant.VALANZHIN, it) } +
            (1..3).map { Gpa("S$it", Plant.SENOMAN, it) }

    fun byId(id: String): Gpa = all.first { it.id == id }
    fun byPlant(plant: Plant): List<Gpa> = all.filter { it.plant == plant }
}

data class WorkPosition(val id: String, val plant: Plant, val title: String) {
    val fullTitle: String get() = "${plant.title} · $title"
}

object PositionList {
    val all = listOf(
        WorkPosition("V_P4", Plant.VALANZHIN, "Поз. 4 Цех 1"),
        WorkPosition("V_P5", Plant.VALANZHIN, "Поз. 5 Цех 2"),
        WorkPosition("V_P66", Plant.VALANZHIN, "Поз. 66 Цех 3"),
        WorkPosition("V_P9", Plant.VALANZHIN, "Поз. 9 БПГ"),
        WorkPosition("V_P140", Plant.VALANZHIN, "Поз. 140 БПГ"),
        WorkPosition("V_P7", Plant.VALANZHIN, "Поз. 7 АВО"),
        WorkPosition("V_P8", Plant.VALANZHIN, "Поз. 8 АВО"),
        WorkPosition("V_P33", Plant.VALANZHIN, "Поз. 33 АВО"),
        WorkPosition("V_P64", Plant.VALANZHIN, "Поз. 64 АВО"),
        WorkPosition("V_P65", Plant.VALANZHIN, "Поз. 65 АВО"),
        WorkPosition("V_OTHER", Plant.VALANZHIN, "Остальные позиции"),
        WorkPosition("S_P2", Plant.SENOMAN, "Поз. 2 БПГ (С)"),
        WorkPosition("S_P3", Plant.SENOMAN, "Поз. 3 Цех сепарации"),
        WorkPosition("S_OTHER", Plant.SENOMAN, "Остальные позиции"),
    )

    fun byId(id: String): WorkPosition = all.first { it.id == id }
    fun byPlant(plant: Plant): List<WorkPosition> = all.filter { it.plant == plant }
}
