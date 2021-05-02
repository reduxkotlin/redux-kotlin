import React, {useState} from "react";
import {SHOW_ACTIVE, SHOW_ALL, SHOW_COMPLETED, ToggleTodo} from 'kotlin-library'

const Todo = ({onClick, completed, title}) => (
    <li
        onClick={onClick}
        style={{
            textDecoration: completed ? 'line-through' : 'none'
        }}
    >
        {title}
    </li>
)

const TodoList = ({todos, toggleTodo}) => (
    <ul>
        {todos.map(todo =>
            (<Todo
                key={todo.id}
                completed={todo.completed}
                title={todo.title}
                onClick={() => toggleTodo(todo.id)}
            />)
        )}
    </ul>
)

const getVisibleTodos = (todos, filter) => {
    switch (filter) {
        case SHOW_ALL:
            return todos
        case SHOW_COMPLETED:
            return todos.filter(t => t.completed)
        case SHOW_ACTIVE:
            return todos.filter(t => !t.completed)
        default:
            throw new Error('Unknown filter: ' + filter)
    }
}

export const VisibleTodoList = ({store}) => {
    const [todos, setTodos] = useState(store.state.todos)
    const [filterState, setFilter] = useState(store.state.visibilityFilter)
    store.subscribe(() => {
        setTodos(store.state.todos)
        setFilter(store.state.visibilityFilter)
    })
    const toggleTodo = (id) => store.dispatch(new ToggleTodo(id))
    return (<TodoList
        todos={getVisibleTodos(todos, filterState)}
        toggleTodo={toggleTodo}
    />)
}