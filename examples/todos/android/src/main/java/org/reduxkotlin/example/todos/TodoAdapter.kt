package org.reduxkotlin.example.todos

import android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.reduxkotlin.example.todos.databinding.ItemTodoBinding
import org.reduxkotlin.examples.todos.Todo
import org.reduxkotlin.examples.todos.ToggleTodo

class TodoAdapter : ListAdapter<Todo, TodoViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TodoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_todo, parent, false)
        return TodoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TodoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class TodoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    fun bind(todo: Todo) {
        val binding = ItemTodoBinding.bind(itemView)
        if (todo.completed) {
            binding.tvTodo.paintFlags = binding.tvTodo.paintFlags or STRIKE_THRU_TEXT_FLAG
        } else {
            binding.tvTodo.paintFlags =
                binding.tvTodo.paintFlags and STRIKE_THRU_TEXT_FLAG.inv()
        }

        binding.tvTodo.text = "• ${todo.text}"
        itemView.setOnClickListener { store.dispatch(ToggleTodo(adapterPosition)) }
    }
}

class DiffCallback : DiffUtil.ItemCallback<Todo>() {
    override fun areItemsTheSame(oldItem: Todo, newItem: Todo): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Todo, newItem: Todo): Boolean {
        return oldItem == newItem
    }
}
