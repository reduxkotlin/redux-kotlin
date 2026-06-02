// app.jsx — router for the docs-site UI kit
function App() {
  const [route, setRoute] = React.useState('home');
  const navigate = (r) => { setRoute(r); window.scrollTo({ top: 0 }); };
  return (
    <div>
      <Navbar route={route} navigate={navigate} />
      {route === 'home' ? <HomePage navigate={navigate} /> : <DocPage />}
      {route === 'home' && <Footer />}
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
