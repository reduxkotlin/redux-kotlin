package org.reduxkotlin.example.todos

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_todo.view.*
import org.reduxkotlin.examples.todos.Todo
import org.reduxkotlin.examples.todos.ToggleTodo
import android.graphics.Paint.STRIKE_THRU_TEXT_FLAG



class TodoAdapter : RecyclerView.Adapter<TodoViewHolder>() {
    var todos: List<Todo> = listOf()
        set(value) {
            field = value
            this.notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(view)
    }

    override fun getItemCount(): Int = todos.size

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        holder.bind(todos[position])
    }
}

class TodoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(todo: Todo) {
        if (todo.completed) {
            itemView.tv_todo.paintFlags = itemView.tv_todo.paintFlags or STRIKE_THRU_TEXT_FLAG
        } else {
            itemView.tv_todo.paintFlags =
                itemView.tv_todo.paintFlags and STRIKE_THRU_TEXT_FLAG.inv()
        }

        itemView.tv_todo.text = "• ${todo.text}"
        itemView.setOnClickListener { store.dispatch(ToggleTodo(adapterPosition))}
    }
}