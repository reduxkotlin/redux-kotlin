import React from "react";
import {AddTodo} from "./AddTodo";
import {Footer} from "./Footer";
import {VisibleTodoList} from "./TodoList";

export const App = ({store}) => (
    <div>
        <AddTodo store={store}/>
        <VisibleTodoList store={store}/>
        <Footer store={store}/>
    </div>
)