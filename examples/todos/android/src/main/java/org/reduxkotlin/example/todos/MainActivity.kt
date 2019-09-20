package org.reduxkotlin.example.todos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.reduxkotlin.StoreSubscription
import org.reduxkotlin.createStore
import org.reduxkotlin.examples.todos.*

/**
 * This is a sample of basic redux behavior.
 * This is NOT best practice for structuring a multiplatform App.
 */


val store = createStore(::rootReducer, AppState())

class MainActivity: AppCompatActivity() {
    private lateinit var storeSubscription: StoreSubscription
    private var adapter = TodoAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        storeSubscription = store.subscribe { render(store.state) }
        btnAddTodo.setOnClickListener {
            val todoText = etTodo.text.toString()
            etTodo.text.clear()
            store.dispatch(AddTodo(todoText))
        }
        btnSelectAll.setOnClickListener { store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_ALL)) }
        btnActive.setOnClickListener { store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_ACTIVE)) }
        btnCompleted.setOnClickListener { store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_COMPLETED)) }

        recyclerView.adapter = adapter

        render(store.state)
    }

    private fun render(state: AppState) {
        adapter.submitList(state.visibleTodos)
        setFilterButtons(state.visibilityFilter)
    }

    private fun setFilterButtons(visibilityFilter: VisibilityFilter) =
        when (visibilityFilter) {
            VisibilityFilter.SHOW_ALL -> {
                btnSelectAll.isSelected = true
                btnActive.isSelected = false
                btnCompleted.isSelected = false
            }
            VisibilityFilter.SHOW_ACTIVE -> {
                btnActive.isSelected = true
                btnSelectAll.isSelected = false
                btnCompleted.isSelected = false
            }
            VisibilityFilter.SHOW_COMPLETED -> {
                btnCompleted.isSelected = true
                btnSelectAll.isSelected = false
                btnActive.isSelected = false
            }
        }
}