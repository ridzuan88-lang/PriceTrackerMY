package com.chewanninja.pricetrackermy

object Categories {
    data class Def(val id: String, val name: String, val color: String)

    val defaults = listOf(
        Def("chicken_meat", "Chicken & Meat", "#E53935"), Def("seafood", "Seafood", "#039BE5"),
        Def("vegetables", "Vegetables", "#43A047"), Def("fruits", "Fruits", "#FB8C00"),
        Def("eggs", "Eggs", "#FDD835"), Def("rice", "Rice", "#8D6E63"),
        Def("cooking_oil", "Cooking Oil", "#F9A825"), Def("groceries", "Groceries", "#6D4C41"),
        Def("dairy_milk", "Dairy & Milk", "#90CAF9"), Def("beverages", "Beverages", "#00ACC1"),
        Def("snacks", "Snacks", "#AB47BC"), Def("household", "Household Products", "#5C6BC0"),
        Def("personal_care", "Personal Care", "#EC407A"), Def("baby", "Baby Products", "#F48FB1"),
        Def("others", "Others", "#78909C")
    )

    // Order matters: first matching rule wins. Malay + English keywords.
    private val rules: List<Pair<String, List<String>>> = listOf(
        "baby" to listOf("diaper", "pampers", "mamypoko", "baby", "bayi", "infant", "dumex", "s-26", "cerelac", "pigeon"),
        "personal_care" to listOf("shampoo", "syampu", "toothpaste", "ubat gigi", "body wash", "lotion", "deodorant", "sanitary", "razor", "conditioner", "facial", "colgate", "soap", "sabun mandi", "dettol", "pantene", "lifebuoy"),
        "household" to listOf("detergent", "pencuci", "tisu", "tissue", "toilet", "dishwash", "clorox", "softener", "garbage", "trash bag", "plastik", "mop", "bleach", "sponge", "sunlight", "breeze", "downy", "comfort", "attack", "daia", "napkin", "aluminium foil", "cling wrap"),
        "cooking_oil" to listOf("minyak masak", "cooking oil", "palm oil", "vegetable oil", "olive oil", "minyak", "buruh", "saji", "seri murni", "knife"),
        "eggs" to listOf("telur", "egg"),
        "rice" to listOf("beras", "rice", "faiza", "jasmine", "basmathi", "basmati"),
        "chicken_meat" to listOf("ayam", "chicken", "daging", "beef", "lamb", "kambing", "meat", "sosej", "sausage", "nugget", "itik", "duck", "bacon", "ham"),
        "seafood" to listOf("ikan", "fish", "udang", "prawn", "shrimp", "sotong", "squid", "kerang", "cockle", "seafood", "ketam", "crab", "salmon", "tuna segar", "siakap", "kembung"),
        "dairy_milk" to listOf("susu", "milk", "cheese", "keju", "yogurt", "yoghurt", "butter", "mentega", "cream", "dutch lady", "marigold", "fresh milk"),
        "beverages" to listOf("air mineral", "mineral", "water", "juice", "jus", "tea", "teh", "coffee", "kopi", "milo", "nescafe", "cola", "coke", "pepsi", "100plus", "soda", "drink", "minuman", "sirap", "f&n", "sprite", "mirinda", "yeo", "nestum"),
        "snacks" to listOf("biskut", "biscuit", "chip", "keropok", "chocolate", "coklat", "snack", "candy", "gula-gula", "cookies", "wafer", "jelly", "popcorn", "tasco", "twisties", "mamee", "cadbury", "oreo", "ice cream", "aiskrim"),
        "fruits" to listOf("epal", "apple", "oren", "orange", "pisang", "banana", "mango", "mangga", "anggur", "grape", "tembikai", "watermelon", "betik", "papaya", "nanas", "pineapple", "durian", "fruit", "buah", "pear", "lemon", "limau", "strawberry", "kiwi", "rambutan", "manggis"),
        "vegetables" to listOf("sayur", "cili", "chilli", "bawang", "onion", "garlic", "carrot", "lobak", "cabbage", "kubis", "sawi", "bayam", "spinach", "tomato", "timun", "cucumber", "kentang", "potato", "brinjal", "terung", "kangkung", "bendi", "long bean", "kacang panjang", "halia", "ginger", "broccoli", "lettuce", "cauliflower", "taugeh", "daun"),
        "groceries" to listOf("sugar", "gula", "flour", "tepung", "garam", "salt", "sauce", "sos", "kicap", "mee", "noodle", "maggi", "bihun", "pasta", "sardin", "sardine", "spaghetti", "bread", "roti", "oats", "cereal", "sambal", "santan", "rempah", "kari", "curry", "tauhu", "tempe", "ramen", "kacang", "tin ", "canned", "mayonnaise", "ketchup", "vinegar", "cuka", "jam", "honey", "madu", "sirap")
    ).map { (id, kws) -> id to kws }

    private val compiled: List<Pair<String, Regex>> = rules.map { (id, kws) ->
        id to Regex("(?:^|[^a-z])(?:" + kws.joinToString("|") { Regex.escape(it) } + ")", RegexOption.IGNORE_CASE)
    }

    fun guess(name: String): String {
        val n = name.lowercase()
        for ((id, rx) in compiled) if (rx.containsMatchIn(n)) return id
        return "others"
    }

    fun productKey(name: String): String =
        name.uppercase().replace(Regex("[^A-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}
