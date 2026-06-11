package org.reduxkotlin.example.todos

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.concurrent.createConcurrentStore
import org.reduxkotlin.example.todos.databinding.ActivityMainBinding
import org.reduxkotlin.examples.todos.*

/**
 * This is a sample of basic redux behavior.
 * This is NOT best practice for structuring a multiplatform App.
 *
 * Redux DevTools is attached only in DEBUG builds via [devToolsEnhancer] (a debug/release
 * source-set split); release builds pass `null` and never link the devtools artifact. To watch
 * actions/state live: run `npx @redux-devtools/cli@4 --port 8000`, then on an emulator the host
 * is 10.0.2.2, or on a USB device run `adb reverse tcp:8000 tcp:8000` (host = "localhost").
 */

val store = createConcurrentStore(::rootReducer, AppState(), enhancer = devToolsEnhancer())

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
