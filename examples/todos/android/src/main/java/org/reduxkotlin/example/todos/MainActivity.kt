package org.reduxkotlin.example.todos

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.devtools.DevToolsConfig
import org.reduxkotlin.devtools.devTools
import org.reduxkotlin.example.todos.databinding.ActivityMainBinding
import org.reduxkotlin.examples.todos.*
import org.reduxkotlin.threadsafe.createThreadSafeStore

/**
 * This is a sample of basic redux behavior.
 * This is NOT best practice for structuring a multiplatform App.
 *
 * Redux DevTools is enabled below. To watch actions/state live:
 *   1. On your dev machine run:  npx @redux-devtools/cli --open   (server + UI on :8000)
 *   2. Run this app on an emulator (host 10.0.2.2 reaches your machine), or on a
 *      USB device after `adb reverse tcp:8000 tcp:8000` (then set host = "localhost").
 */

val store = createThreadSafeStore(
    ::rootReducer,
    AppState(),
    devTools(
        DevToolsConfig(
            name = "Todos",
            host = "10.0.2.2",
            logger = { Log.d("DevTools", it) },
        ),
    ),
)

class MainActivity : AppCompatActivity() {
    private lateinit var storeSubscription: StoreSubscription
    private lateinit var binding: ActivityMainBinding
    private var adapter = TodoAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
                    or WindowInsetsCompat.Type.ime(),
            )
            v.updatePadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        storeSubscription = store.subscribe { render(store.state) }
        binding.btnAddTodo.setOnClickListener {
            val todoText = binding.etTodo.text.toString()
            binding.etTodo.text.clear()
            store.dispatch(AddTodo(todoText))
        }
        binding.btnSelectAll.setOnClickListener { store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_ALL)) }
        binding.btnActive.setOnClickListener { store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_ACTIVE)) }
        binding.btnCompleted.setOnClickListener { store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_COMPLETED)) }

        binding.recyclerView.adapter = adapter

        render(store.state)
    }

    private fun render(state: AppState) {
        adapter.submitList(state.visibleTodos)
        setFilterButtons(state.visibilityFilter)
    }

    private fun setFilterButtons(visibilityFilter: VisibilityFilter) = when (visibilityFilter) {
        VisibilityFilter.SHOW_ALL -> {
            binding.btnSelectAll.isSelected = true
            binding.btnActive.isSelected = false
            binding.btnCompleted.isSelected = false
        }

        VisibilityFilter.SHOW_ACTIVE -> {
            binding.btnActive.isSelected = true
            binding.btnSelectAll.isSelected = false
            binding.btnCompleted.isSelected = false
        }

        VisibilityFilter.SHOW_COMPLETED -> {
            binding.btnCompleted.isSelected = true
            binding.btnSelectAll.isSelected = false
            binding.btnActive.isSelected = false
        }
    }
}
