package org.reduxkotlin.example.todos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.example.todos.databinding.ActivityMainBinding
import org.reduxkotlin.examples.todos.*
import org.reduxkotlin.threadsafe.createThreadSafeStore

/**
 * This is a sample of basic redux behavior.
 * This is NOT best practice for structuring a multiplatform App.
 */

val store = createThreadSafeStore(::rootReducer, AppState())

class MainActivity : AppCompatActivity() {
  private lateinit var storeSubscription: StoreSubscription
  private lateinit var binding: ActivityMainBinding
  private var adapter = TodoAdapter()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
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

  private fun setFilterButtons(visibilityFilter: VisibilityFilter) =
    when (visibilityFilter) {
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
