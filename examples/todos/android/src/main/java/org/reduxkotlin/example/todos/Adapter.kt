package org.reduxkotlin.example.todos

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.reduxkotlin.examples.todos.Todo

class Adapter: RecyclerView.Adapter<TodoViewHolder>() {
    var todos: List<Todo> = listOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
    }

    override fun getItemCount(): Int = todos.size

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
    }

}

class TodoViewHolder(view: View): RecyclerView.ViewHolder(view) {
    fun bind(todo: Todo) {
        view.
    }
}