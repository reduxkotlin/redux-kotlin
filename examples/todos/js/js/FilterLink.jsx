import React, {useState} from "react";
import {SetVisibilityFilter} from 'kotlin-library';

const Link = ({active, children, onClick}) => (
    <button
        onClick={onClick}
        disabled={active}
        style={{
            marginLeft: '4px'
        }}
    >
        {children}
    </button>
)

export const FilterLink = ({store, children, filter}) => {
    const [filterState, setFilter] = useState(store.state.visibilityFilter)
    store.subscribe(() => setFilter(store.state.visibilityFilter))

    const active = filterState === filter
    const onClick = () => store.dispatch(new SetVisibilityFilter(filter))
    return (
        <Link active={active} children={children} onClick={onClick}/>
    )
}