import { CustomExtensionList } from "./components/CustomExtensionList";
import { FixedExtensionList } from "./components/FixedExtensionList";
import { FileUploadForm } from "./components/FileUploadForm";
import "./App.css";

function App() {
  return (
    <main>
      <h1>File Extension Blocker</h1>
      <p>파일 확장자에 따라 특정 형식의 파일을 첨부하거나 전송하지 못하도록 제한합니다.</p>

      <section>
        <FixedExtensionList />
        <CustomExtensionList />
      </section>

      <hr />

      <section>
        <FileUploadForm />
      </section>
    </main>
  );
}

export default App;
