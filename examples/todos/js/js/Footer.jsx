import React from "react";
import {SHOW_ACTIVE, SHOW_ALL, SHOW_COMPLETED} from 'kotlin-library'
import {FilterLink} from "./FilterLink";


export const Footer = ({store}) => {
    return (
        <div>
            <span>Show: </span>
            <FilterLink store={store} filter={SHOW_ALL}>All</FilterLink>
            <FilterLink store={store} filter={SHOW_ACTIVE}>Active</FilterLink>
            <FilterLink store={store} filter={SHOW_COMPLETED}>Completed</FilterLink>
        </div>
    )
}