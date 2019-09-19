package org.reduxkotlin.example.todos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
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
        btn_add_todo.setOnClickListener {
            val todoText = et_todo.text.toString()
            et_todo.text.clear()
            store.dispatch(AddTodo(todoText))
        }
        btn_select_all.setOnClickListener { store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_ALL)) }
        btn_active.setOnClickListener { store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_ACTIVE)) }
        btn_completed.setOnClickListener { store.dispatch(SetVisibilityFilter(VisibilityFilter.SHOW_COMPLETED)) }

        recycler_view.layoutManager = LinearLayoutManager(this)
        recycler_view.adapter = adapter

        render(store.state)
    }

    private fun render(state: AppState) {
        adapter.todos = state.visibleTodos
        setFilterButtons(state.visibilityFilter)
    }

    private fun setFilterButtons(visibilityFilter: VisibilityFilter) {
        when (visibilityFilter) {
            VisibilityFilter.SHOW_ALL -> {
                btn_select_all.isSelected = true
                btn_active.isSelected = false
                btn_completed.isSelected = false
            }
            VisibilityFilter.SHOW_ACTIVE -> {
                btn_active.isSelected = true
                btn_select_all.isSelected = false
                btn_completed.isSelected = false
            }
            VisibilityFilter.SHOW_COMPLETED -> {
                btn_completed.isSelected = true
                btn_select_all.isSelected = false
                btn_active.isSelected = false
            }
        }
    }
}