import { Routes, Route, Navigate } from "react-router-dom";
import { isAuthed } from "./api";
import Layout from "./components/Layout";
import Login from "./pages/Login";
import Overview from "./pages/Overview";
import Versions from "./pages/Versions";
import Devices from "./pages/Devices";
import Activation from "./pages/Activation";
import Clients from "./pages/Clients";

function Protected({ children }) {
  return isAuthed() ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        element={
          <Protected>
            <Layout />
          </Protected>
        }
      >
        <Route path="/" element={<Navigate to="/overview" replace />} />
        <Route path="/overview" element={<Overview />} />
        <Route path="/versions" element={<Versions />} />
        <Route path="/devices" element={<Devices />} />
        <Route path="/activation" element={<Activation />} />
        <Route path="/clients" element={<Clients />} />
      </Route>
      <Route path="*" element={<Navigate to="/overview" replace />} />
    </Routes>
  );
}
