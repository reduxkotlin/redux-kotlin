import React from "react";
import {AddTodo as AddTodoReducer} from 'kotlin-library';

export const AddTodo = ({store}) => {
    let input

    const dispatch = store.dispatch

    return (
        <div>
            <form
                onSubmit={e => {
                    e.preventDefault()
                    if (!input.value.trim()) {
                        return
                    }
                    dispatch(new AddTodoReducer(input.value))
                    input.value = ''
                }}
            >
                <input ref={node => (input = node)}/>
                <button type="submit">Add Todo</button>
            </form>
        </div>
    )
}