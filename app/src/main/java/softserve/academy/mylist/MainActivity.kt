package softserve.academy.mylist

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import softserve.academy.mylist.ui.theme.MyListTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.withContext



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyListTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        ShoppingListScreen()
                    }
                }
            }
        }
    }
}

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    val name: String,
    val isBought: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Int = 0
)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY id DESC")
    fun getAllItems(): List<ShoppingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: ShoppingItem)

    @Update
    fun updateItem(item: ShoppingItem)

    @Delete
    fun deleteItem(item: ShoppingItem)
}

@Database(entities = [ShoppingItem::class], version = 1)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        fun getInstance(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ShoppingListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: ShoppingDao = ShoppingDatabase.getInstance(application).shoppingDao()
    private val _shoppingList = mutableStateOf<List<ShoppingItem>>(emptyList())
    val shoppingList: State<List<ShoppingItem>> get() = _shoppingList

    init {
        loadShoppingList()
    }

    private fun loadShoppingList() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = dao.getAllItems()
            withContext(Dispatchers.Main) {
                _shoppingList.value = items
            }
        }
    }

    fun addItem(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newItem = ShoppingItem(name = name)
            dao.insertItem(newItem)
            val updatedList = dao.getAllItems()
            withContext(Dispatchers.Main) {
                _shoppingList.value = updatedList
            }
        }
    }

    fun toggleBought(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = _shoppingList.value
            if (index in currentList.indices) {
                val item = currentList[index]
                val updatedItem = item.copy(isBought = !item.isBought)
                dao.updateItem(updatedItem)

                val newList = currentList.toMutableList()
                newList[index] = updatedItem

                withContext(Dispatchers.Main) {
                    _shoppingList.value = newList
                }
            }
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteItem(item)
            loadShoppingList()
        }
    }
}

@Composable
fun ShoppingItemCard(
    item: ShoppingItem,
    onToggleBought: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val scale by animateFloatAsState(
        targetValue = if (item.isBought) 1.2f else 1f,
        label = "checkbox-scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(Color.LightGray, MaterialTheme.shapes.large)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isBought,
            onCheckedChange = { onToggleBought() },
            modifier = Modifier.scale(scale)
        )
        Text(
            text = item.name,
            modifier = Modifier.weight(1f),
            fontSize = 18.sp
        )
        IconButton(onClick = { onDelete() }) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Item")
        }
    }
}

@Composable
fun AddItemButton(addItem: (String) -> Unit = {}) {
    var text by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Add Item") }
        )
        Button(onClick = {
            if (text.isNotEmpty()) {
                addItem(text)
                text = ""
            }
        }) {
            Text("Add")
        }
    }
}

@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel = viewModel(
    factory = ShoppingListViewModelFactory(LocalContext.current.applicationContext as Application)
)) {
    val scope = rememberCoroutineScope()
    val visibleItems = remember { mutableStateListOf<Int>() }

    val shoppingList by viewModel.shoppingList

    LaunchedEffect(shoppingList) {
        visibleItems.clear()
        visibleItems.addAll(shoppingList.map { it.id })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            AddItemButton { viewModel.addItem(it) }
        }
        itemsIndexed(shoppingList, key = { _, item -> item.id }) { _, item ->
            AnimatedVisibility(
                visible = visibleItems.contains(item.id),
                exit = fadeOut()
            ) {
                ShoppingItemCard(
                    item = item,
                    onToggleBought = { viewModel.toggleBought(shoppingList.indexOf(item)) },
                    onDelete = {
                        scope.launch {
                            visibleItems.remove(item.id)
                            delay(300)
                            viewModel.deleteItem(item)
                        }
                    }
                )
            }
        }
    }
}

class ShoppingListViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShoppingListViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ShoppingListScreenPreview() {
    ShoppingListScreen()
}

@Composable
fun ShoppingItemCardPreview() {
    var toggleState by remember { mutableStateOf(false) }
    ShoppingItemCard(
        ShoppingItem("Молоко", isBought = toggleState)
    ) {
        toggleState = !toggleState
    }
}
