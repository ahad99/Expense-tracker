package com.example.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.*

data class CategoryInfo(
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val isCustom: Boolean = false
)

object ExpenseCategories {
    val FOOD = CategoryInfo("Food & Dining", Icons.Default.Restaurant, CategoryFood)
    val TRANSPORT = CategoryInfo("Transportation", Icons.Default.DirectionsCar, CategoryTransport)
    val HOUSING = CategoryInfo("Housing & Rent", Icons.Default.Home, CategoryHousing)
    val BILLS = CategoryInfo("Bills & Utilities", Icons.Default.ReceiptLong, CategoryBills)
    val SHOPPING = CategoryInfo("Shopping", Icons.Default.ShoppingBag, CategoryShopping)
    val ENTERTAINMENT = CategoryInfo("Entertainment", Icons.Default.Movie, CategoryEntertainment)
    val HEALTH = CategoryInfo("Health & Fitness", Icons.Default.FitnessCenter, CategoryHealth)
    val OTHER = CategoryInfo("Other", Icons.Default.MoreHoriz, CategoryOther)

    val defaultList = listOf(
        FOOD,
        TRANSPORT,
        HOUSING,
        BILLS,
        SHOPPING,
        ENTERTAINMENT,
        HEALTH,
        OTHER
    )

    val list = defaultList

    private val customColors = listOf(
        Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFFF9800),
        Color(0xFFE91E63), Color(0xFF3F51B5), Color(0xFF009688),
        Color(0xFF8BC34A), Color(0xFFFF5722)
    )

    fun getCategoryList(customCategoriesString: String = ""): List<CategoryInfo> {
        val customNames = customCategoriesString
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val customList = customNames.mapIndexed { index, name ->
            CategoryInfo(
                name = name,
                icon = Icons.Default.Label,
                color = customColors[index % customColors.size],
                isCustom = true
            )
        }

        return defaultList + customList
    }

    fun getCategory(name: String, customCategoriesString: String = ""): CategoryInfo {
        val all = getCategoryList(customCategoriesString)
        return all.find { it.name.equals(name, ignoreCase = true) }
            ?: CategoryInfo(
                name = name,
                icon = Icons.Default.Label,
                color = CategoryOther,
                isCustom = true
            )
    }
}
