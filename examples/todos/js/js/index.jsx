import React from 'react';
import ReactDOM from 'react-dom';
import {App} from "./App"
import {store} from "./components"

ReactDOM.render(
    <App store={store}/>,
    document.getElementById('app')
);